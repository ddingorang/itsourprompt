# 배포 backend 장애 추적 설계 (제안)

> 상태: **제안 — 미구현**.
> 기준 브랜치: `BE-main` (**26cd175**). 대상: `backend/`. 배포는 단일 EC2 · **단일 인스턴스**.
>
> 근거 문서 셋:
> - [`spring-boot-4.1-observability.md`](./spring-boot-4.1-observability.md) — Boot 4.1이 기본 제공하는 것
> - [`spring-boot-4.1-structured-fields.md`](./spring-boot-4.1-structured-fields.md) — 구조화 로그 필드를 만드는 법 (**실행 검증됨**)
> - [`postgres-log-querying.md`](./postgres-log-querying.md) — 로그를 SQL로 읽는 법 (**이번 범위 밖. 부록 A 참조**)

## 1. 타겟

> **배포된 백엔드에서 일어난 모든 요청과 LLM 호출을 추적할 수 있고, 사용자가 에러를 신고하면 그게 무엇이었는지 확인해 설명할 수 있다.**

재발 방지는 이 정보로 **팀이 하는 일**이지, 이 설계가 자동화하는 게 아니다. CI·알림·자동 분석은 범위 밖이다.

**전제 — 이 설계의 크기를 정하는 사실들:**

| | |
|---|---|
| 현재 사용자 | **0명.** 배포는 돼 있으나 아무도 안 쓴다 |
| 유입 시점 | **이번 주.** 이 작업을 하면서 동시에 받는다 |
| 남은 기간 | **1주일** |
| 신고 경로 | 사용자 → 개발팀 → 개발/인프라팀이 조사 |

"모든 것"이 목표에 들어간 이유는 **무엇이 정상인지 모르면 이상을 판단할 수 없어서**다. 실패만 남기면 "200이었는데 사용자는 실패로 느낀 경우"에 로그가 비어 있다(§8.3).

그리고 **로그는 비대칭이다.** 안 찍은 줄은 소급 복구가 안 되고, 찍은 줄은 언제든 나중에 읽을 수 있다. 사용자가 들어오기 **전에** 넣어야 하는 것과 뒤에 넣어도 되는 것이 이 기준으로 갈린다(§11).

## 2. 이미 있는 것

`BE-main`에 상당 부분이 **이미 구현돼 있다.** 새로 만드는 것보다 **비어 있는 곳을 메우는 것**이 이 설계의 실체다.

| 있는 것 | 위치 |
|---|---|
| 요청 id 생성(UUID) → MDC `requestId` | `global/logging/RequestLogFilter` |
| `X-Request-Id` 응답 헤더 — **`doFilter` 전에 심는다** | 〃 (`:48`) |
| 요청 소요시간 측정 (`durationMs`) | 〃 |
| 5xx `error` / 4xx `warn` 로깅 (method·path·status·durationMs) | 〃 |
| 필터 체인에서 새는 예외 포착 (`Unhandled request failure`) | 〃 |
| `GET /api/me` 401은 정상이므로 `info`로 격하하는 특례 | 〃 |
| 로그 패턴에 requestId 출력 — `[req:%X{requestId:-}]` | `application.yml:67` |
| **catch-all** `@ExceptionHandler(Exception.class)` + `log.error` | `global/exception/GlobalExceptionHandler` |
| 500 응답이 `X-Request-Id`로 문의하라고 안내 | 〃 |
| 게스트 세션(쿠키) 발급 · 로그인 시 이전 | `guest/GuestSessionService`, `guest/GuestSessionFilter` |
| LLM 호출 로그 14줄 | `ai/OpenAiCodeGenerator` 8, `ai/OpenAiFeedbackGenerator` 6 |

로그 호출지점 **38곳 / 12개 파일**. 컨트롤러 4개에 엔드포인트 **14개**.

> **헤더가 `doFilter` 전에 심긴다는 사실이 설계를 하나 줄인다.** 시큐리티가 끊는 401·403, catch-all 500, 정적 404까지 **모든 응답에 이미 `X-Request-Id`가 실린다.** 응답 본문에 따로 넣을 필요가 없다(§4).

## 3. 비어 있는 것

### 3.1 치명적 — LLM 경로에 `requestId`가 안 붙는다

`ai/AiCallExecutor`가 AI 호출을 **별도 스레드에 넘긴다**:

```java
private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
public <T> T call(Callable<T> aiCall, long timeoutMinutes) {
    Future<T> future = executor.submit(aiCall);   // ← 여기서 스레드가 갈린다
```

MDC는 ThreadLocal이다. 로그 패턴이 준비돼 있어도 **워커 스레드에서 찍히는 줄에는 `[req:]`가 비어서 나간다.**

**실제로 비는 건 14줄이 아니라 `OpenAiCodeGenerator.runLoop` 안의 4개다** — `request started` · `round finished` · `tool round cap reached` · `finalize call failed`. `OpenAiFeedbackGenerator`의 6줄과 `OpenAiCodeGenerator`의 타임아웃 로그는 전부 호출자(톰캣) 스레드에서 찍혀 지금도 `[req:]`가 차 있다. 설계 결정은 그대로 유효하다 — MDC를 전파하면 Spring AI·RestClient 내부 로그까지 덮는다. 다만 **육안 검증은 반드시 `[OPENAI RUN] request started` / `round finished`로 해야 한다**(§13.2). 피드백 경로로 검증하면 고치기 전에도 통과해서 "이미 되네?"라는 잘못된 결론이 나온다.

이 앱에서 가장 자주 터지는 경로이고, 최근 커밋 세 개가 전부 이 경로의 502·타임아웃 얘기다(`13ba7a4`·`1d9623b`·`f3e6ee9`). **추적 체계가 정작 제일 필요한 곳에서 끊겨 있다.**

### 3.2 실패한 LLM 호출의 프롬프트가 사라진다

`AttemptService:128-135`:

```java
generated = ...generateCode(problemView, attempt, userPrompt);
} catch (RuntimeException exception) {
    recordFailedCalls(attemptId, LlmCallPurpose.CODE, exception);   // 토큰·errorType만
    throw exception;
}
attemptWriter.appendTurn(attemptId, owner, userPrompt, generated, idempotencyKey);  // ← 성공했을 때만
```

`Turn.userPrompt`는 `appendTurn`에서만 저장되는데, 실패 경로는 그 줄에 닿기 전에 빠진다. `AttemptLlmCall`이 갖는 건 `model`·토큰 4종·`latencyMs`·`cost`·`status`·`errorType`뿐이다 — **입력이 없다.** 클래스 주석이 직접 말한다: *"턴이 저장되지 않은 실패 호출도 남겨야 하고"*.

**터진 요청의 입력만 골라서 사라진다.** LLM 실패를 확인·설명하려면 그 프롬프트가 있어야 한다.

### 3.3 로그 필드가 문자열 안에 갇혀 있다

액세스 로그가 `log.error("Request failed | method={} path={}...", ...)` 형태라 `method`·`status`·`durationMs`가 **메시지 문자열의 일부**다. `grep`으로 한 줄 찾기는 되지만 **거르고·묶고·정렬하는 게 안 된다.** "어느 엔드포인트가 몇 번 실패했나"에 답할 수 없다.

### 3.4 catch-all이 4xx를 500으로 바꾸고 있다 — **실측 확인, 버그**

`ErrorHandlingApiTest`로 쟀다. **네 경우 전부 500이 나간다:**

| 요청 | 기대 | **실제** | catch-all에 도달한 예외 |
|---|---|---|---|
| `GET /api/does-not-exist` | 404 | **500** | `NoResourceFoundException` |
| `DELETE /api/problems` | 405 | **500** | `HttpRequestMethodNotSupportedException` |
| 깨진 JSON 본문 POST | 400 | **500** | `HttpMessageNotReadableException` |
| `Content-Type: text/plain` POST | 415 | **500** | `HttpMediaTypeNotSupportedException` |

응답 본문은 전부 `{"code":"internal-server-error",...}`이고, 로그에는 `Unexpected API exception`이 **스택트레이스와 함께 ERROR로** 4번 찍혔다. `MethodArgumentNotValidException`만 전용 핸들러가 있어 400을 지킨다.

**이건 관측성 개선이 아니라 버그 수정이다.** 그리고 이 문서의 목적에 직접 걸린다 — **클라이언트 실수가 전부 ERROR 로그로 쌓이면** 우리가 짓는 조사 체계의 에러 로그가 오탐으로 뒤덮인다.

부수 피해: 프런트가 "내 잘못"과 "서버 잘못"을 구분 못 하고, 재시도 판단이 뒤집힌다.

### 3.5 5분마다 도는 스케줄러가 같은 오탐을 만든다

`ProblemSyncScheduler`:

```java
@Scheduled(initialDelayString = "0", fixedDelayString = "${PROBLEM_SYNC_INTERVAL_MS:300000}")
public void sync() {
    try { ... }
    catch (Exception exception) {
        log.error("문제 동기화에 실패했습니다. 다음 주기에 다시 시도합니다.", exception);
    }
}
```

셋이 겹친다:

1. **5분 주기 = 하루 288회.** 실패가 지속되면 **288줄의 ERROR + 스택트레이스**가 매일 쌓인다
2. **예외를 삼킨다.** 클래스 주석이 명시한 설계다 — 서비스는 안 멈추지만 **아무도 안 본다**
3. **MDC가 없다.** 스케줄러 스레드라 걸러낼 키가 없다

§3.4와 **정확히 같은 실패 모드**다. 하루 1,000 요청에 실제 5xx가 몇 건이라면, GitLab 토큰 만료 하나가 에러 로그의 대부분을 차지한다. 4xx 오탐을 걷어내고 이걸 남겨두면 헛일이다.

### 3.6 나머지 공백

| 공백 | 영향 |
|---|---|
| 정상 2xx 요청이 로그에 없다 | "200이었는데 사용자는 실패로 느낀 경우"를 판별 못 한다 (§8.3) |
| 로그가 stdout에만 있다 | `docker rm` 하면 사라진다. 재배포가 곧 로그 유실 |
| **CORS `exposedHeaders`가 없다** | `WebConfig.addCorsMappings`에 없어 **브라우저 JS가 `X-Request-Id`를 못 읽는다** |
| `CodeRunResultListener`에 요청 id가 없다 | 워커 결과 처리 실패가 요청과 안 묶인다 |
| `X-Request-Id`를 클라이언트가 정할 수 있다 | 같은 값을 반복해 보내면 추적 id의 유일성이 깨진다 |

## 4. 뺀 것과 그 이유

| 뺀 것 | 왜 | 되살릴 때 |
|---|---|---|
| **PostgreSQL 조회층** | §9.2 참조. 질의 5개 중 4개가 `jq` 한 줄이고, 이번 주 규모는 **4만 행**이라 인덱스가 풀 문제가 없다. **원본이 파일이라 미뤄도 잃는 게 0**이다 | jq로 안 되는 질의(백분위·조인)가 실제로 나올 때. 적재 방식은 **검증 완료**(부록 A) |
| **응답 본문에 `requestId`** | `RequestLogFilter:48`이 `doFilter` **전에** 헤더를 심어 401 포함 전 응답에 이미 실린다. 프런트 변경량이 헤더·본문 양쪽 3줄로 같은데 BE가 1줄 대 ~40줄이다. 게다가 `apiClient.parseError`의 **본문 없는 폴백 분기**(프록시 오류·502 HTML)는 헤더만 덮는다 | 헤더로 안 되는 경우가 나올 때 |
| **`heap`·`dbActive`·`dbWait`·주기 상태 로그** | `dbActive`/`dbWait`은 이 앱에서 일어난다는 **증거가 없다**. 주기 로그의 근거는 "평소 값을 알아야 이상치를 안다"인데 **사용자가 0명이라 평소가 항상 0**이다 | 원인 불명 5xx가 실제로 한 번 나면. 그때는 무엇이 필요한지도 알게 된다 |
| **`user`·`guestId` MDC** | `guestId`는 서버가 심은 쿠키라 **사용자가 말할 수 없다.** `path`의 attemptId가 로그인·게스트 모두를 **0줄로** 덮는다(프런트 URL이 `/attempts/:id`) | 어템프트를 넘는 사람 단위 분석이 필요할 때 |
| **Micrometer Tracing** | `RequestLogFilter`가 이미 같은 일을 한다 | 워커 구간까지 추적을 잇거나 트레이스 백엔드를 둘 때 |
| **로그 수집기** (Loki / ELK) | 단일 EC2 · 단일 인스턴스 | 인스턴스가 늘거나 장기 보존이 필요할 때 |
| **메트릭 시계열** (Prometheus/Grafana) | 주기 상태 로그를 뺐으므로 대체재를 논할 대상도 없다. 하루 1,000 요청 규모에 시계열이 답할 질문이 없다 | 로그로 감당 안 되는 해상도가 필요할 때 |
| **actuator** | 현재 `SecurityConfig`가 `anyRequest().permitAll()`이라 **넣는 즉시 전 엔드포인트가 무인증 공개**된다 | 컨테이너 healthcheck나 로드밸런서가 필요해질 때 |
| **알림 (Slack 등)** | 기준선이 없어 임계값을 못 정한다. 노이즈가 된 알림은 무시하게 된다 | 며칠치 로그로 기준선이 잡힌 뒤 |
| **CI 테스트 스테이지** | 재발 방지는 **팀의 의지**지 이 설계의 책임이 아니다. Testcontainers라 Docker 빌드 스테이지 안에서 못 돌고(`-x test`는 강제다), Jenkins 파이프라인 변경은 레포 밖이다. 게다가 BE-main 테스트가 지금 깨져 있어 켜면 배포가 막힌다 | `BE/fix-failing-tests` 머지 후, 데모가 끝난 뒤 |
| **로그를 앱이 런타임에 DB로 쓰기** | §6 참조 | 안 되살린다 — 부록 A가 같은 편익을 준다 |
| **Spring AI 프롬프트/응답 **전량** 로깅** | 자유 텍스트는 로그 파일에 안 넣는다(§7). **실패 건만 DB에** 남긴다(슬라이스 4) | — |
| **워커(buildandtest) 구간** | 별도 프로세스 · replica 2개. `RunResultMessage`에 attemptId조차 없어 계약 변경이 필요하다. `runId` → `code_run` 행 → attemptId로 간접 연결은 된다(슬라이스 6) | 워커 쪽 장애가 실제로 문제될 때 |

**애플리케이션 의존성 추가 0개.** `build.gradle`을 건드리지 않는다.

---

## 5. 로그를 어떻게 남기나 — 구조 결정

이 절이 나머지 전부의 전제다. **실행 검증된 사실**에 기반한다 (`spring-boot-4.1-structured-fields.md`).

### 5.1 콘솔은 평문, 파일은 JSON

```yaml
logging:
  file:
    name: ${LOG_FILE:/var/log/app/backend.log}   # ← 없으면 아래 format.file이 조용히 무동작한다
  structured:
    format:
      file: ecs                                   # console 미지정 → 평문 유지
    json:
      context:
        prefix: app                               # §5.3
  pattern:
    console: "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %5p ${PID:-} --- [%thread] [req:%X{requestId:-}] %logger{39} : %msg %kvp%n"
  logback:
    rollingpolicy:
      max-file-size: 50MB      # 기본 10MB
      max-history: 14          # 기본 7
      total-size-cap: 2GB      # 기본 0B = 무제한 ← 반드시 지정한다
```

- **콘솔**: 사람이 `docker logs`로 바로 읽는다. 진입장벽 0
- **파일**: 기계가 읽는다. §9에서 `jq`로 질의한다. 회전 파일은 기본 `.gz` 압축

> ⚠️ `logging.file.name`을 빼면 file appender 자체가 안 생겨서 `format.file: ecs`가 **아무 일도 안 한다.** 조용히 실패하므로 배포 직후 확인을 **필수 게이트**로 둔다(§13.1).

### 5.2 스코프 값은 MDC, 이벤트 값은 KeyValuePair

```java
log.atInfo()
   .addKeyValue("method", method)
   .addKeyValue("route", route)
   .addKeyValue("path", path)
   .addKeyValue("status", status)          // int → JSON number
   .addKeyValue("durationMs", durationMs)
   .addKeyValue("inflight", inflight)
   .log("Request");
```

| | 담는 것 | 왜 |
|---|---|---|
| **MDC** | `requestId` · `job` · `runId` | 그 요청/작업의 **모든** 로그 줄에 붙어야 한다 |
| **KeyValuePair** | `method` `route` `path` `status` `durationMs` `inflight` | **그 한 줄에만** 해당하는 값 |

**KVP가 자바 타입을 보존한다.** `status`가 JSON number로 나가서 `.app.status >= 500`이 그대로 된다. MDC는 `Map<String,String>`이라 무조건 문자열이 된다 — 수치 비교가 필요한 필드는 반드시 KVP여야 한다.

콘솔에서는 `%X`가 MDC만, `%kvp`가 KVP만 찍어 **중복 출력이 없다**. 파일(ECS)에서는 둘이 같은 객체로 병합된다.

**`%kvp`는 기본값(따옴표)을 쓴다.** `{NONE}`은 따옴표를 안 붙여 기존 `status=502` 형태를 지키지만, **KVP 값에 공백을 넣으면 안 된다**는 제약이 따라온다 — 그리고 그 제약은 강제되지 않는다. `exceptionType`·`job` 같은 문자열 값이 늘어나는 상황에서 누군가 공백 있는 값을 넣으면 **파일 JSON은 멀쩡한 채 콘솔만 조용히 망가진다.** 반면 `{NONE}`이 지키려던 grep 용례(`status=502` 형태로 거르기)는 §9.1 어디에도 없다. 실수하면 `status="502"`가 눈에 보이는 쪽이 낫다.

> 콘솔 패턴에 `%kvp`를 **반드시** 넣어야 한다. `%msg`는 KVP를 받지 않으므로, 안 넣으면 콘솔에서 필드가 통째로 사라진다 — 명백한 회귀다.

### 5.3 `context.prefix`로 예약어 충돌을 막는다

ECS 포맷터가 쓰는 이름(`message`·`log`·`service`·`process`·`error`·`tags`·`ecs`·`@timestamp`)과 MDC/KVP 키가 겹치면 예외가 나고, **같은 스레드의 다음 로그 한 줄까지 앞부분이 중복된 깨진 JSON으로 나간다** — 관계없는 로그를 먹는다.

그리고 `error`·`tags`는 **조건부**다. `error`는 그 이벤트에 **예외가 있을 때만** 충돌한다. `.addKeyValue("error", e.getMessage())`는 누군가 자연스럽게 쓸 이름인데, **평소엔 멀쩡하다가 예외를 함께 로깅하는 순간에만** 터진다.

`context.prefix: app`을 걸면 충돌 검사가 최종 멤버 경로(`app.error`)에 대해 돌아 **내장 이름과의 충돌이 원천 차단된다.** 실측으로 확인됐다.

CI 테스트를 켜지 않기로 했으므로(§4) **가드 테스트라는 대안이 실제로 없다.** 기계적 차단이 이것뿐이다.

**프리픽스가 못 막는 것 하나**: MDC 키와 KVP 키가 **서로** 같으면 여전히 충돌한다. §5.2 표대로 서로소로 유지한다.

### 5.4 결과물

```
[콘솔] 2026-08-01T15:04:03.300+09:00 ERROR 1 --- [nio-9090-exec-3] [req:cc33dd44-...]
       c.p.g.l.RequestLogFilter : Request failed method="POST" route="/api/attempts/{id}/submit"
       path="/api/attempts/77/submit" status="500" durationMs="41230" inflight="12"

[파일] {"@timestamp":"...","log":{"level":"ERROR","logger":"c.p.g.l.RequestLogFilter"},
        "message":"Request failed",
        "app":{"requestId":"cc33dd44-...","method":"POST",
               "route":"/api/attempts/{id}/submit","status":500,"durationMs":41230,"inflight":12},
        "error":{"type":"java.lang.NullPointerException","stack_trace":"..."},
        "ecs":{"version":"8.11"}}
```

`jq`에서 쓸 실제 경로 — **예상과 다르니 주의**:

| 내용 | 경로 | 비고 |
|---|---|---|
| 타임스탬프 | `."@timestamp"` | `timestamp` 아님 |
| 레벨 | `.log.level` | `level` 아님. **접두어 영향 없이 최상위 유지** |
| MDC · KVP | `.app.<키>` | 접두어는 여기에만 걸린다 |
| 예외 | `.error.type` / `.error.stack_trace` | **없으면 멤버 자체가 생략**된다 → `null` |

스택트레이스는 개행이 박힌 **단일 문자열**이라 JSON 이스케이프되고, **1줄 = 1레코드**가 유지된다. `jq`가 그대로 먹는다.

`@timestamp`는 항상 UTC `Z`이고 조건 없이 모든 이벤트에 나온다. 단 **나노초 시계에 소수부 자릿수가 가변**이라(`...41.788054908Z` / `...41.788Z` / `...41Z`) **문자열 정렬이 시간순과 다르다** — `Z`(0x5A) > `.`(0x2E)라 정각이 뒤로 밀린다. 시간순으로 볼 일이 있으면 파싱해서 정렬한다. 적재할 때 `timestamptz`가 필수인 이유이기도 하다(부록 A).

---

## 6. 로그를 DB에 직접 쓰지 않는 이유

"SQL로 읽고 싶다"는 요구는 맞다. 그런데 **쓰는 곳과 읽는 곳을 나눠야** 한다. 근거는 둘이다.

**1. 보존이 공짜다.** 파일은 설정 두 줄이면 끝난다:

```yaml
max-history: 14
total-size-cap: 2GB
```

DB에 쓰면 `DELETE FROM app_log WHERE ts < now() - interval '14 days'`를 도는 크론을 직접 짜야 한다. 안 짜면 무한히 큰다.

**2. 조사 창구가 갈린다.** DataSource 오류로 기동이 실패하면 그 줄은 stdout에만 남는다 — DB 연결 **전**이라 DB로는 한 줄도 못 쓴다. 배포가 잦은 주에 정확히 이 부류가 터진다. DB 로깅이면 "기동 실패는 `docker logs`, 나머지는 SQL"로 조사가 두 곳으로 쪼개진다. 파일이면 둘 다 한 곳이다.

**폐기한 근거들** — 초안에 있었으나 사실 확인에서 무너졌다:

| 폐기 | 왜 |
|---|---|
| ~~DB가 죽으면 그때 로그가 없다~~ | 고려 범위 밖으로 정리 |
| ~~트랜잭션 상호작용(롤백)~~ | `REQUIRES_NEW`로 해결된다 — **이 레포에 이미 있다**(`IdempotencyGuard:33,72`). 게다가 이 설계의 로그는 애초에 트랜잭션 밖에서 나온다: 액세스 로그는 `RequestLogFilter`, 예외 로그는 트랜잭션 **종료 후**의 `@ExceptionHandler`, LLM 로그는 `@Transactional`이 없는 `AttemptService` 경유 |
| ~~요청 경로 지연~~ | 하루 1,000 요청 규모에서 논할 값이 아니다 |
| ~~서비스 DB 오염~~ | 별도 DB면 해소된다 |
| ~~LLM 대기 중 커넥션 고갈~~ | **사실이 아니다.** `AttemptService`에 `@Transactional`이 하나도 없고 `open-in-view: false`라 대기 동안 커넥션은 반납 상태다 |

**대신 부록 A가 같은 편익을 준다** — 파일이 원본, PostgreSQL은 필요해지면 만드는 파생 조회층.

---

## 7. 무엇을 어디에 저장하나

§6은 "로그 이벤트"를 다뤘다. 그런데 **실패한 LLM 호출의 프롬프트**는 로그 이벤트인가 도메인 데이터인가? 이걸 가르는 규칙이 필요하다.

### 7.1 두 축

**축 1 — 서비스 코드가 그 값을 다시 읽나?** (기능 의존)
**축 2 — 그 값이 자유 텍스트인가?** (사람이 타이핑한 내용)

|  | **서비스가 읽는다** | **서비스가 안 읽는다** |
|---|---|---|
| **자유 텍스트** | DB — `Turn.userPrompt` (`FileReplay`가 파생), `aiSummary`, `feedback` | **DB** — 실패 호출의 프롬프트 |
| **시스템이 만든 사실** | DB — `AttemptLlmCall` 토큰·비용 (`AttemptWebMapper` → `LlmUsageSummary` → API), `code_run`, `IdempotencyRecord`, `SyncState` | **로그** — 액세스 로그, 스택트레이스, `inflight`, 스케줄러 성공/실패 |

> **자유 텍스트는 DB.** 서비스가 안 읽어도 그렇다.
> **시스템이 만든 사실은** — 서비스가 읽으면 DB, 안 읽으면 로그.

### 7.2 "자유 텍스트는 DB"의 근거 — 판정 가능한 사실 셋

"민감하다"가 아니라 로그 파일이 **구조적으로 못 하는 것** 셋이다. 전부 예/아니오로 답한다.

1. **지울 수 있나** — DB는 `DELETE ... WHERE attempt_id = ?`다. 로그는 회전·`.gz` 압축된 과거 파일까지 열어 특정 줄을 지워야 한다. 사실상 불가능
2. **언제 사라지나** — 로그는 `max-history: 14` · `total-size-cap: 2GB`가 **내가 정한 시점이 아니라 용량이 찬 시점에** 지운다. DB 행은 내가 정할 때까지 남는다
3. **누가 보나** — 로그 파일은 EC2·`docker exec` 접근권이 있으면 전부. DB는 계정과 비밀번호

### 7.3 경계 사례

**`username`은 사용자가 정한 문자열인데 로그에 넣어도 되나?** — 된다. **역할이 다르다.** "무엇을 **가리키나**"면 식별자, "무엇을 **말했나**"면 내용이다. `username`·`attemptId`·`requestId`·`path`·`status`는 가리킨다. `userPrompt`·`aiSummary`·`feedback`은 말한다. **로그에는 가리키는 것만 넣는다.**

**`Turn.aiSummary`는 AI가 썼는데 왜 DB인가?** — 축 1로 들어간다. 서비스가 읽어 화면에 그린다. 그리고 자유 텍스트라 축 2로도 DB다.

**`AttemptLlmCall.errorType`은 서비스가 안 읽을 수도 있는데 왜 DB인가?** — **행 응집이 규칙을 이긴다.** 같은 행의 나머지(토큰·비용)가 DB에 있어야 하는데 한 필드만 로그로 보내면 조인이 끊긴다. **이미 DB에 있어야 할 행의 필드는 쪼개지 않는다.**

### 7.4 이 규칙이 잡아낸 것

슬라이스 2의 초안 코드는 이랬다:

```java
log.warn("표준 웹 예외를 {}로 변환합니다: {}", status.value(), exception.toString());
```

400을 내는 `HttpMessageNotReadableException`의 `toString()`은 Jackson 파싱 오류를 담고, **그 오류가 문제가 된 입력 조각을 인용한다.** 즉 사용자가 보낸 본문이 로그 파일로 나간다 — §7.2의 셋을 전부 뒤집어쓴 채로. 고치면:

```java
log.atWarn()
   .addKeyValue("status", status.value())
   .addKeyValue("exceptionType", exception.getClass().getSimpleName())
   .log("표준 웹 예외를 상태 코드로 변환합니다");
```

예외 **클래스 이름**은 시스템이 만든 사실이라 로그가 맞다. 본문 조각은 아니다.

같은 검사를 슬라이스 1이 넣는 KVP에도 돌린다 — `method`·`route`·`path`·`status`·`durationMs`·`inflight`는 **전부 가리키는 값**이라 통과한다.

> **§7이 §6과 충돌하는 것처럼 보이는 지점.** 실패 프롬프트를 DB에 넣는 건 §6이 금지한 게 **아니다.** §6이 금지한 건 *요청 경로에 INSERT를 추가하는 로깅 파이프라인*이다. `AttemptService:130`이 이미 `attemptWriter.recordFailure(...)`로 `attempt_llm_call` 행을 쓰고 있고, 프롬프트는 **그 행에 컬럼 하나를 더하는 것**이다. 요청당 INSERT가 늘지 않는다.
>
> 구분선: **로그 이벤트를 DB로 보내지 마라. 하지만 도메인 데이터는 원래 DB다 — 실패한 호출의 입력도 도메인 데이터다.**

---

## 8. 슬라이스

총 **~94줄 + 설정.**

### 슬라이스 1 — 액세스 로그 구조화 · `route` · 전량화 · `inflight` (~45줄 + 설정)

**이 설계의 중심이다.** §5의 설정과 함께 들어가야 §9의 `jq`가 성립한다.

**(a) 필드를 KVP로 옮긴다** (§5.2). 메시지는 `"Request"` / `"Request rejected"` / `"Request failed"`만 남는다.

**(b) `route` 추가** — 경로 템플릿. `path`엔 어템프트 id가 박혀 있어 엔드포인트별 집계가 불가능하다. 값은 `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`에서 얻는다.

**핸들러에 도달 못 한 요청은 `route`를 비운다.** 시큐리티가 끊는 401·403, 정적 리소스 404가 여기 해당한다. `path`로 폴백하면 `/attempts/42` 같은 값이 route 집계에 섞여 route를 만든 이유가 사라진다. 비워두면 `.app.route == null`이 **"핸들러 미도달"이라는 의미 있는 필터**가 된다.

> ⚠️ **저절로 비지는 않는다 — 실측으로 확인됐다.** 시큐리티가 끊는 401·403은 DispatcherServlet 진입 전이라 속성 자체가 없지만, `/api/does-not-exist`는 `SimpleUrlHandlerMapping`이 `/**`로 매칭하면서 `BEST_MATCHING_PATTERN_ATTRIBUTE = "/**"`를 먼저 세팅한 뒤 `NoResourceFoundException`을 던진다. `AccessLogApiTest`를 가드 없이 돌려 `route`가 `"/**"`로 나오는 것을 확인했다.
>
> `/**`는 엔드포인트를 가리키는 값이 아니라 "아무 데도 안 걸렸다"는 뜻이므로 **`null`과 동일하게 취급한다**(3줄).

**(c) 정상 2xx도 남긴다.** 현재는 5xx·4xx·`/api/me` 401 특례만 남긴다.

1. **사용자가 "에러 났어요"라고 할 때 서버는 200이었을 수 있다.** 프런트가 요청을 안 보냈거나, CORS에 막혔거나, 200인데 응답 내용이 이상한 경우. 실패만 남기면 이때 **로그에 아무것도 없다** (§9.3 판별표)
2. **§1이 "모든 것"을 목표로 잡았다.** 그게 근거다

**(d) `inflight`** — `AtomicInteger` 증감 요청당 2회. 근거가 코드로 확인되는 유일한 상태 값이다: `AiCallExecutor.call()`이 `future.get(timeout)`으로 **호출 스레드(톰캣 스레드)를 최대 5분 붙잡는다.** 요청이 몰리면 스레드가 마른다.

**로그량**: ECS JSON 한 줄 ~450바이트(추정). 하루 1,000 요청이면 **450KB/일**로 `total-size-cap: 2GB`가 사실상 무한이다. 실제 크기는 배포 후 `head -1 | wc -c`로 확정한다.

> **나중에 줄일 조건**: 트래픽이 늘어 한 경로가 로그를 지배할 때 그 경로만 제외하거나 샘플링한다. **반대 방향(안 남기다가 필요해짐)은 되살릴 수 없다.**

### 슬라이스 2 — 표준 웹 예외의 상태 코드 복구 (~15줄, **버그 수정**)

§3.4에서 실측한 버그를 고친다. **catch-all 안에서 `ErrorResponse`를 분기**한다:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
    // Spring이 이미 상태 코드를 아는 표준 웹 예외(404·405·415 등)는 그 상태 코드를 그대로 쓰고
    // 스택은 남기지 않는다 — 클라이언트 잘못이지 버그가 아니다.
    // 5xx를 내는 ErrorResponse 구현체(AsyncRequestTimeoutException 503 등)는 걸러서 아래로 보낸다.
    if (exception instanceof ErrorResponse errorResponse
            && errorResponse.getStatusCode().is4xxClientError()) {
        HttpStatusCode status = errorResponse.getStatusCode();
        log.atWarn()
           .addKeyValue("status", status.value())
           .addKeyValue("exceptionType", exception.getClass().getSimpleName())
           .log("표준 웹 예외를 상태 코드로 변환합니다");
        return ResponseEntity.status(status).body(new ApiErrorResponse(codeFor(status), messageFor(status)));
    }
    log.error("Unexpected API exception", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiErrorResponse("internal-server-error", INTERNAL_ERROR_MESSAGE));
}
```

> ⚠️ **`HttpMessageNotReadableException`은 `ErrorResponse`가 아니다 — `javap`(spring-web 7.0.8) 실측.** 404·405·415는 전부 `ServletException` 계열이라 `ErrorResponse`를 구현하지만, 본문 파싱 400은 `HttpMessageConversionException` → `NestedRuntimeException` 계열이라 구현하지 않는다. `ResponseEntityExceptionHandler`가 이걸 400으로 만드는 건 인터페이스가 아니라 `handleHttpMessageNotReadable` 전용 분기이기 때문이다.
>
> 위 가드만 넣으면 **깨진 JSON 케이스는 계속 500이다.** `@ExceptionHandler(HttpMessageNotReadableException.class)` 전용 핸들러(~10줄)를 따로 둔다 — 아래 표가 배정한 `invalid-request`/400을 그대로 쓴다. 예외 메시지는 §7.4대로 응답에도 로그에도 싣지 않는다.
>
> `ApiErrorResponse`에는 `of(...)` 팩터리가 **없다**(2필드 record). 기존 19개 핸들러처럼 `new ApiErrorResponse(...)`를 쓴다.

**예외 메시지를 로그에 안 넣는 이유는 §7.4다** — `HttpMessageNotReadableException.toString()`이 사용자 본문 조각을 인용한다.

**예외를 다시 던지지 않는 이유** — 초안은 `throw exception;`이었는데 그러면 `DefaultHandlerExceptionResolver` → `/error` 재디스패치 → `BasicErrorController`로 넘어가 **본문이 `{code, message}`에서 `{timestamp, status, error, path}`로 바뀐다.** 컨트롤러의 `@Schema(implementation = ApiErrorResponse.class)` 약 20곳이 거짓이 된다. 위처럼 분기하면 **한 곳만 고치고 계약이 유지된다.**

부수 효과가 크다:

- 4xx가 `error` → `warn`으로 내려가 **에러 로그의 오탐이 사라진다**
- 스택트레이스를 안 남겨 로그량이 준다

**`codeFor`/`messageFor`는 프런트 조율이 블로커가 아니다.** `apiClient.parseError`가 모르는 코드를 `data.code ?? 'unknown-error'`로 받고 `message`를 그대로 보여준다. 404/405/415는 클라 실수라 프런트가 분기할 일도 없다.

| 상태 | `code` | 프런트 변경 |
|---|---|---|
| 404 | `not-found` | 0줄 |
| 405 | `method-not-allowed` | 0줄 |
| 415 | `unsupported-media-type` | 0줄 |
| 400 (본문 파싱) | `invalid-request` — **기존 코드 재사용** (`MethodArgumentNotValidException` 핸들러와 동일, `API_ERROR_CODES`에 이미 있음) | 0줄 |

메시지는 상태별 고정 한국어 문구다. 예외 메시지를 그대로 노출하지 않는다.

검증은 `ErrorHandlingApiTest`가 이미 있다 — **지금은 4개 다 빨간불이다.**

### 슬라이스 3 — LLM 스레드에 MDC 전파 (~10줄)

§3.1을 메운다.

```java
public <T> T call(Callable<T> aiCall, long timeoutMinutes) throws ... {
    Map<String, String> context = MDC.getCopyOfContextMap();
    Future<T> future = executor.submit(() -> {
        if (context != null) {
            MDC.setContextMap(context);
        }
        try {
            return aiCall.call();
        } finally {
            MDC.clear();
        }
    });
    ...
}
```

호출 스레드는 `future.get(timeout)`으로 블록되므로 호출자 쪽 MDC는 그대로다. 작업 스레드에만 심으면 된다.

### 슬라이스 4 — 실패한 LLM 호출의 프롬프트를 DB에 (~10줄)

§3.2를 메운다. **§7의 규칙대로 로그가 아니라 DB다** — 자유 텍스트이고, 지울 수 있어야 하고, 회전이 지우면 안 되고, 열람 범위가 달라야 한다.

- `attempt_llm_call`에 `user_prompt text` 컬럼 추가 (DDL은 손으로 — `ddl-auto: none`이고 Flyway가 없다)
- `AttemptLlmCall.failed(...)`에 인자 하나 추가
- `AttemptService.recordFailedCalls(...)`가 프롬프트를 넘긴다

`recordFailedCalls`가 이미 DB에 쓰고 있으므로 **요청당 INSERT가 늘지 않는다.**

### 슬라이스 5 — 스케줄러 오탐 차단 (~8줄)

§3.5를 메운다.

- **`job` MDC** — `MDC.put("job", "problem-sync")` + `finally` 정리. `jq 'select(.log.level=="ERROR" and .app.job==null)'`로 **사람 트래픽 에러만** 볼 수 있게 된다. 반대로 배치만 보기도 된다
- **연속 실패 강등** — 첫 실패는 `ERROR`, 이어지는 실패는 `WARN`, 복구되면 `INFO` 한 줄. 288줄이 ERROR 1 + WARN 287이 되고 **실패의 시작 시각이 오히려 선명해진다**

### 슬라이스 6 — 리스너 `runId` (~5줄)

`CodeRunResultListener`는 요청 스레드가 아니라 `requestId`가 없다. `RunResultMessage`에는 **attemptId가 없다** — `runId`뿐이다. `runId`를 MDC에 넣는다. `runId` → `code_run` 행 → attemptId로 이어진다. attemptId를 메시지에 직접 넣는 건 워커 계약 변경이라 §4에서 제외한 범위다.

### 슬라이스 7 — CORS 응답 헤더 노출 (**1줄**)

`WebConfig.addCorsMappings`에 `exposedHeaders`가 없어 **브라우저가 `X-Request-Id`를 못 읽는다.**

```java
.exposedHeaders(RequestLogFilter.REQUEST_ID_HEADER)
```

프런트는 3줄이면 된다 — `apiClient`가 예외를 던지는 자리에 `Response`가 이미 있다:

```ts
throw new ApiError(response.status, await parseError(response), response.headers.get('X-Request-Id'));
```

> 배포가 same-origin이면 브라우저가 응답 헤더를 제한 없이 읽으므로 이 줄이 필요 없을 수도 있다. `VITE_API_BASE_URL`이 Jenkins 빌드 시 주입돼 레포에서 확정이 안 된다(`frontend/Dockerfile:9`는 `http://`, `application.yml`의 허용 목록은 `https://`로 어긋나 있다). **1줄짜리 보험이라 확정할 가치가 없다. 그냥 넣는다.**

### 슬라이스 8 — 504 응답 문구 (~5줄)

```java
new ApiErrorResponse("run-timeout", exception.getMessage())
new ApiErrorResponse("feedback-timeout", exception.getMessage())
```

**확인 결과 고쳐야 한다 — 교체 확정.** `FeedbackTimeoutException`의 메시지가 **`"AI feedback request timed out."`** 이고 `GlobalExceptionHandler`가 그걸 응답 본문에 그대로 싣는다. 즉 영문 문구가 이미 사용자에게 나가고 있다. 두 곳 다 상태별 고정 한국어 상수로 바꾼다.

`code`(`run-timeout`/`feedback-timeout`)는 **그대로 둔다** — 프런트의 재시도 분기가 그 값에 걸려 있다(§10.2).

### 슬라이스 9 — `X-Request-Id` 클라이언트 지정 제거 (**-5줄**)

```java
String suppliedRequestId = request.getHeader(REQUEST_ID_HEADER);
if (suppliedRequestId != null && SAFE_REQUEST_ID.matcher(suppliedRequestId).matches()) {
    return suppliedRequestId;      // ← 삭제
}
```

**추적 id의 유일성이 이 체계 전체의 전제인데 지금은 그게 클라이언트 손에 있다.** 정규식 검증은 헤더 인젝션은 막지만 **중복은 안 막는다** — 고정값 하나를 계속 보내면 그 사용자의 모든 요청이 한 id로 뭉쳐 §9.1의 `grep 'req:...'`가 무의미해진다.

그리고 **아무도 안 쓴다.** `apiClient.createHeaders`가 붙이는 건 `Content-Type`·`Idempotency-Key`·`Accept` 셋뿐이다. 중복 방지가 필요한 자리에는 이미 `Idempotency-Key`가 있다.

나중에 프런트 상관관계가 필요해지면 `clientRequestId`를 **별도 KVP**로 받는다 — 서버 id를 덮어쓰지 않고 나란히 남기는 형태로.

---

## 9. 사용자가 신고했을 때 하는 일

두 층이다. **급하면 `grep`, 파고들면 `jq`.**

### 9.1 콘솔 — 빠른 확인

```bash
C=<backend-컨테이너>

# id를 알려줬다. UUID 앞 8자면 대개 좁혀진다
docker logs $C 2>&1 | grep 'req:3f9a1c2e'

# 어템프트 번호 — 프런트 URL이 /attempts/:id라 주소창을 읽어줄 수 있다
docker logs $C 2>&1 | grep 'attempts/42'

# 시간대
docker logs $C --since '2026-08-01T15:00' --until '2026-08-01T15:15' 2>&1

# 최근 실패 (스택트레이스까지 보려면 -A)
docker logs $C 2>&1 | grep -A20 ERROR | tail -100
```

### 9.2 `jq` — 거르고·묶고·정렬

파일이 **JSON Lines**라 `jq`가 그대로 먹는다. 회전 파일까지 합치는 헬퍼:

```bash
logs() { cat /var/log/app/backend.log; zcat /var/log/app/backend.log.*.gz 2>/dev/null; }
```

```bash
# 한 요청의 전체 흐름 + 예외
logs | jq 'select(.app.requestId // "" | startswith("3f9a1c2e"))'

# 한 어템프트에서 일어난 일 전부 (로그인/게스트 구분 없이)
logs | jq 'select(.app.path // "" | contains("/attempts/42"))'

# 사람 트래픽 에러만 — 배치 오탐 제외 (슬라이스 5)
logs | jq 'select(.log.level == "ERROR" and .app.job == null)'

# 엔드포인트별 5xx — grep으로는 못 하던 것
logs | jq -s '[.[] | select(.app.status >= 500)]
              | group_by(.app.route)
              | map({route: .[0].app.route, n: length, maxMs: (map(.app.durationMs) | max)})
              | sort_by(-.n)'

# 느린 요청 상위 20
logs | jq -s 'sort_by(-(.app.durationMs // 0)) | .[:20]
              | .[] | {ts: .["@timestamp"], route: .app.route, ms: .app.durationMs}'

# 핸들러에 도달조차 못 한 요청 (401·403·정적 404)
logs | jq 'select(.app.route == null and (.app.status // 0) >= 400)'
```

**규모가 이걸 정당화한다.** 하루 1,000 요청 × 40일이면 4만 줄이다. `jq -s`가 즉시 끝난다. 인덱스가 풀 문제가 없다.

**이걸로 안 되는 질의**는 백분위와 앱 테이블 조인 정도다. 실제로 필요해지면 부록 A로 간다.

### 9.3 "에러 났다는데 로그에 에러가 없다"

흔한 경우다. 슬라이스 1(c)로 모든 요청을 남기므로 여기서 갈린다:

| 액세스 로그에 | 뜻 |
|---|---|
| 그 요청이 **있고 2xx** | backend는 정상 처리했다. 프런트·응답 내용·사용자 오해 쪽 |
| 그 요청이 **있고 느리다** | 타임아웃으로 사용자가 실패로 인식. `durationMs`가 증거 |
| 그 요청이 **아예 없다** | 요청이 서버에 도달조차 안 했다. CORS·네트워크·프런트가 안 보냄 |

이 구분은 **정상 요청까지 남겨야만** 가능하다.

---

## 10. 500이 났을 때

### 10.1 사용자에게 보여주는 것 — 지금도 괜찮다

```json
{"code": "internal-server-error",
 "message": "서버 내부 오류가 발생했습니다. X-Request-Id를 포함해 관리자에게 문의해주세요."}
```

예외 메시지를 노출하지 않고 고정 문구를 준다. 슬라이스 7(CORS)로 프런트가 그 헤더를 실제로 읽을 수 있게 된다. 504 두 곳만 손본다(슬라이스 8).

### 10.2 재시도 — 이미 방침이 있다, 추가 작업 없음

`handleGenerationFailure` 주석: *"AI 생성 실패는 같은 요청을 다시 보내면 통과하는 경우가 많다."*

- **502**(`ai-provider-error`) · **504**(타임아웃) → 재시도 가능
- **500**(`internal-server-error`) → 재시도해도 같은 결과일 가능성이 높다

응답 `code`가 이미 구분하므로 프런트가 502·504에만 재시도 버튼을 띄우면 된다.

> ⚠️ **자동 재시도는 넣지 않는다.** LLM 호출은 건당 비용이 있고 **최대 5분** 걸린다. 자동 재시도는 비용과 지연을 배로 만들면서 사용자에겐 "더 오래 멈춘 것"으로 보인다.

### 10.3 운영자가 아는 방법 — 지금은 없다, 그리고 지금은 그게 맞다

500이 나도 사용자가 신고해야 안다. 알림은 §4에서 뺐다. 대신 주기 점검이 그 자리를 메운다:

```bash
logs | jq -s '[.[] | select(.log.level == "ERROR" and .app.job == null)]
              | group_by(.app.route) | map({route: .[0].app.route, n: length}) | sort_by(-.n)'
```

`.app.job == null`이 슬라이스 5 덕에 배치 오탐을 걷어낸다.

### 10.4 데이터 정합성 — 짚어만 둔다

500이 트랜잭션 중간에 나면 어템프트가 어중간한 상태로 남을 수 있다. **도메인 문제라 이 설계의 범위 밖이다.**

---

## 11. 구현 순서

**세 층으로 끊는다.** 사용자가 이번 주에 들어오므로 **비대칭한 것부터** 넣는다 — 안 찍은 줄은 소급 복구가 안 된다.

| 층 | 포함 | 줄 | 이것만으로 |
|---|---|---|---|
| **A** — 첫 이틀 | 슬라이스 1(액세스 로그·`route`·전량화·`inflight`) + §5 설정 + 슬라이스 3(LLM MDC) + 슬라이스 7(CORS) | **~56** | §1이 성립한다. 모든 요청이 추적되고, LLM 경로가 안 끊기고, 사용자가 id를 말할 수 있다 |
| **B** | A + 슬라이스 2(catch-all 버그) + 슬라이스 5(스케줄러) | **~79** | 에러 로그가 **진짜 에러만** 센다. 오탐 두 경로가 막힌다 |
| **C** | B + 슬라이스 4(실패 프롬프트) + 6(`runId`) + 8(504) + 9(클라 지정 제거) | **~94** | 실패 재현 가능, 워커 결과까지 연결 |

**A만 비대칭이다.** B·C는 사용자가 들어온 뒤에 넣어도 잃는 게 없다(버그는 그동안 오탐을 쌓지만 나중에 걸러낼 수 있다).

**§12의 인계 2건은 오늘 보낸다.** 코드보다 리드타임이 길고, A의 파일 JSON이 거기 걸린다.

슬라이스마다 커밋한다. 브랜치 `BE/observability` (worktree `.claude/worktrees/observability`, `BE-main` 26cd175 기준).

---

## 12. 인계 항목

**배포(레포 밖 — Jenkins가 컨테이너를 띄운다. `backend/Dockerfile:2` 주석 근거. `docker-compose.yml`에 백엔드가 없다)**

1. **로그 볼륨 마운트** — `-v /srv/promptstudio/logs:/var/log/app`
   안 붙여도 `total-size-cap: 2GB`가 디스크를 묶고 컨테이너 안에 파일은 생기지만, **컨테이너 재생성 시 로그가 사라진다.** 1주일 스프린트는 재배포가 잦다
   > **블로커는 아니다.** §5.1 설정은 마운트와 독립이다. 설정을 먼저 넣고 마운트는 병행 요청한다. 마운트가 이틀 밀려도 그 사이 로그는 컨테이너 안에 살아 있다
2. **도커 로그 회전** — `--log-opt max-size=50m --log-opt max-file=5`. 콘솔 출력은 여전히 docker 드라이버가 받는다

**프런트**

3. 에러 화면에 `requestId` 노출 — `apiClient`에서 `response.headers.get('X-Request-Id')`를 `ApiError`에 실어 화면까지. **3줄.** §9.1의 어템프트 번호 경로가 있어 전제 조건은 아니다

**경로 통일**: 로그 파일은 컨테이너 안 `/var/log/app/backend.log`, 호스트 볼륨 `/srv/promptstudio/logs`.

---

## 13. 확인 방법

### 13.1 배포 직후 — **필수 게이트**

§5.1의 조용한 실패(`logging.file.name` 누락 시 file appender 자체가 안 생김)를 여기서 잡는다. **이게 실패하면 §9.2의 `jq` 층 전체가 없는 상태로 배포된 것이고, 며칠 뒤에 알면 그 며칠이 통째로 사라진다.**

```bash
C=<backend-컨테이너>

docker exec $C sh -c 'test -s /var/log/app/backend.log' \
  && docker exec $C head -1 /var/log/app/backend.log | jq -e '.["@timestamp"]' >/dev/null \
  && echo OK || echo "FAIL: 파일 로깅 미동작"

docker exec $C head -1 /var/log/app/backend.log | jq -e '.app' >/dev/null \
  && echo "OK: context.prefix 적용됨" || echo "FAIL: app 객체 없음"

docker inspect $C --format '{{json .HostConfig.LogConfig}}'      # 도커 로그 회전
curl -i https://i15a505.p.ssafy.io/api/problems | grep -i x-request-id
```

### 13.2 로컬에서 눈으로

**LLM 경로(슬라이스 3)가 제일 중요하다.** OpenAI 키를 일부러 틀리게 주고 코드 생성을 요청하면 실패 경로가 돈다.

**볼 대상을 `[OPENAI RUN] request started` / `round finished` 두 줄로 못 박는다.** 이 둘만 워커 스레드에서 찍힌다(§3.1) — 피드백 경로나 타임아웃 로그는 호출자 스레드라 **고치기 전에도 `[req:]`가 차 있어** 검증이 되지 않는다. 그 두 줄의 `[req:...]`가 **비어 있지 않은지** 본다. 같은 실행에서 `attempt_llm_call.user_prompt`에 프롬프트가 들어갔는지도 확인한다(슬라이스 4).

나머지: 콘솔에 `%kvp` 필드가 보이는가 / 파일이 JSON으로 생겼는가 / `app` 객체 안에 MDC·KVP가 들어갔는가 / 2xx 요청도 한 줄 남는가.

### 13.3 테스트로 못 박을 것

| 무엇 | 왜 |
|---|---|
| 404·405·415·잘못된 JSON의 **상태코드와 본문** | 슬라이스 2의 회귀 방지. **필수** — `ErrorHandlingApiTest`가 이미 있고 지금 빨간불이다 |
| 응답 헤더 `X-Request-Id`가 401·500 양쪽에 실리는지 | 슬라이스 7의 전제 |

> 기존 4xx 검증 10개는 전부 **도메인 예외**(전용 핸들러 있음)라 catch-all에 닿지도 않는다. 표준 웹 예외 경로는 **테스트가 전혀 없었다.**
>
> **Jenkins가 테스트를 건너뛴다**(`backend/Dockerfile`: `-x test`). Testcontainers라 Docker 빌드 스테이지 안에서 못 도는 게 원인이고, 고치려면 Jenkins 파이프라인에 별도 스테이지가 필요하다 — **레포 밖이고 이번 주 범위 밖이다**(§4). 이 테스트들은 배포를 막아주지 못한다. 머지 전에 사람이 로컬에서 돌린다.

---

## 14. 서버가 느려지지 않나

**측정하지 않았다.** 구조에서 나온 추론이다.

| 항목 | 비용 |
|---|---|
| MDC 복사 (LLM 호출당 1회) | 맵 복사 1회. 최대 5분짜리 호출 앞에선 무시 가능 |
| `inflight` 카운터 | `AtomicInteger` 증감 요청당 2회 |
| 액세스 로그 (요청당 1줄, 콘솔+파일) | 동기 쓰기 ~수 µs. 요청 처리가 수십 ms~수 분이라 무시 가능 |

**리스크 — 5xx 폭주 시 동기 로그 I/O.** Logback 기본 어펜더는 동기다. 전체 요청이 5xx로 도는 상황이면 스택트레이스 문자열화와 쓰기가 요청 스레드를 잡아 장애를 악화시킬 수 있다. `AsyncAppender`로 막을 수 있지만 **지금은 넣지 않는다** — 복잡도(큐 크기·버림 정책·종료 시 유실) 대비 이득이 불확실하고, 하루 1,000 요청 규모에서 발생하기 어렵다.

---

## 15. 남은 결정 / 미확인

- **`codeFor`/`messageFor` 문구** — 상태별 한국어 문구를 무엇으로 할지. 프런트 변경은 0줄이라 **블로커는 아니다**(§8 슬라이스 2)
- **로그 한 줄 실제 크기** — 450바이트는 추정이다. `head -1 | wc -c`로 바로 확정된다
- **볼륨 마운트 완료 여부** — §12-1. 담당자 확인 필요. §13.1 게이트로 검증한다
- **`AsyncAppender` 도입 여부** — 지금은 안 넣는다(§14). 5xx 폭주가 관측되면 재검토
- **배포 오리진이 same-origin인지** — `VITE_API_BASE_URL`이 레포 밖에서 주입돼 확정이 안 된다. 슬라이스 7이 1줄짜리 보험이라 확정 없이 진행한다

**해결된 것**: ~~catch-all이 4xx를 삼키는지~~(실측·§3.4) · ~~`route`가 비는 구간 처리~~(비운다·슬라이스 1b) · ~~MDC를 어디서 채우고 지울지~~(`user`/`guestId`를 뺐으므로 소멸) · ~~`dbActive`/`dbWait`의 가치~~(뺐다·§4) · ~~`X-Request-Id` 클라이언트 지정 허용 여부~~(제거·슬라이스 9) · ~~적재 방식~~(측정 완료·부록 A)

---

## 부록 A — PostgreSQL 조회층 (**이번 범위 밖, 검증은 끝남**)

`jq`로 안 되는 질의(백분위, 앱 테이블 조인)가 실제로 나오면 그때 만든다. **원본이 파일이라 미뤄도 잃는 것이 없다** — 로그는 이미 디스크에 있고, 필요해진 날 저녁에 부어도 과거 데이터가 그대로 들어온다.

**만드는 법은 [`postgres-log-querying.md`](./postgres-log-querying.md)의 "권장안 — 순서대로 실행하는 것"을 그대로 따른다.** 여기 옮겨 적지 않는다. 거기에 DDL(§B-2)·적재 스크립트(3단계)·`FORMAT csv`와 제어문자 구분자의 근거(§A-1)·`ON_ERROR`(§A-4)·생성 컬럼을 못 쓰는 이유(§B-1)·GIN 배제(§B-3)·별도 DB(§C-1)·`TRUNCATE` 보존(§C-2)이 전부 있다.

그 문서가 이 계획과 맞물리는 지점만 적는다:

- **`ts`는 `timestamptz`여야 한다.** `text`로 두면 §5.4의 가변 소수부 때문에 정렬이 시간순과 어긋난다. 캐스팅 시 마이크로초로 절삭되므로 1ns 차이의 순서가 필요하면 보조 키를 쓴다
- **`app` 접두어를 경로에 넣어야 한다** — `raw -> 'app' ->> 'requestId'`. `context.prefix: app`(§5.3) 때문이다. `rename.*`을 쓸 거면 `rename.app.durationMs` 형태여야 하고 `rename.durationMs`는 무시된다
- **인덱스는 행이 수십만을 넘고 나서 만든다.** 하루 1,000 요청 × 40일이면 4만 행이라 seq scan이 즉시 끝난다

**적재 검증은 끝났다.** 일회용 컨테이너(`docker run -d --rm postgres:18.4`)에 실제 ECS 형태 JSON(정상 3 + 깨진 1)을 흘려 넣어 **전부 통과**했다 — 제어문자 구분자/인용자 수용, `\copy … FROM PSTDIN`이 `docker exec -i` 파이프를 읽음, `ON_ERROR ignore`가 깨진 줄 건너뜀(`COPY 3`, `1 row was skipped`), 스택트레이스의 개행·이스케이프 보존, 특수문자 왕복(`comma, quote " backslash \ tab ⇥ pipe |`), `timestamptz` 정렬이 소수부 혼재에도 시간순, KVP 숫자 비교, `route` 집계.

부수 확인: 나노초가 마이크로초로 절삭되고(`.788054908Z` → `.788055`), **404 줄은 `route`가 비었다** — 슬라이스 1(b)가 그대로 두기로 한 그 동작이다.

버릴 때: `DROP DATABASE promptstudio_logs`. **원본이 파일이라 언제나 안전하다.**
