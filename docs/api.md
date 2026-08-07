# Prompt Studio 백엔드 API 레퍼런스

## 개요

| 항목 | 값 |
|---|---|
| Base URL (로컬) | `http://localhost:9090` |
| 컨텍스트 패스 | 없음 (모든 경로가 `/api/...`로 시작) |
| Swagger UI | `http://localhost:9090/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:9090/v3/api-docs` |
| 허용 Origin (CORS) | `http://localhost:5173` (모든 컨트롤러에 `@CrossOrigin`) |

전체 엔드포인트 목록:

| Method | 경로 | 인증 | 리소스 |
|---|---|---|---|
| GET | `/api/problems` | 불필요 | 문제 |
| GET | `/api/problems/{id}` | 불필요 | 문제 |
| GET | `/api/problems/{id}/ranking` | 불필요 | 랭킹 |
| POST | `/api/attempts` | 불필요 | 어템프트 |
| GET | `/api/attempts/{id}` | 불필요 | 어템프트 |
| POST | `/api/attempts/{id}/turns` | 불필요 | 어템프트 |
| POST | `/api/attempts/{id}/submit` | 불필요 | 어템프트 |
| GET | `/api/attempts/{id}/feedback` | 불필요 | 피드백 |
| POST | `/api/attempts/{id}/runs` | 불필요 | 코드 실행 |
| POST | `/api/attempts/{id}/turns/{ordinal}/runs` | 불필요 | 코드 실행 |
| GET | `/api/attempts/{id}/runs` | 불필요 | 코드 실행 |
| GET | `/api/attempts/{id}/runs/{runId}` | 불필요 | 코드 실행 |
| POST | `/api/auth/signup` | 불필요 | 인증 |
| POST | `/api/auth/login` | 불필요 | 인증 |
| POST | `/api/auth/logout` | 불필요 (멱등) | 인증 |
| GET | `/api/me` | **필요** | 인증 |
| GET | `/api/me/attempts` | **필요** | 인증 |

"인증 불필요"는 로그인을 요구하지 않는다는 뜻이고, **아무나 무엇이든 할 수 있다는 뜻은 아니다.** 어템프트 계열 경로에서 쓰기는 언제나 소유자만 할 수 있고, 읽기는 제출 완료된 어템프트에 한해 누구에게나 열린다 — 아래 [소유권](#소유권)을 볼 것.

## 인증 방식

**세션 + HttpOnly 쿠키**다. JWT 같은 토큰을 클라이언트가 저장하지 않는다.

1. `POST /api/auth/login` 성공 → 서버가 인증 정보를 HTTP 세션에 저장하고 `Set-Cookie: JSESSIONID=...; HttpOnly`를 내려준다.
2. 이후 브라우저가 쿠키를 자동 전송하므로 클라이언트는 별도 헤더를 붙이지 않는다.
3. 로그인 여부는 클라이언트 저장값이 아니라 `GET /api/me` 응답(200/401)으로 판단한다.

인증이 필요한 경로는 `/api/me/**` 하나뿐이고, 나머지는 전부 공개다. 즉 문제·어템프트 API는 비로그인으로도 호출된다. 인증이 필요한 경로에 세션 없이 접근하면 Spring Security 필터가 컨트롤러 진입 전에 401 `unauthenticated`로 차단한다.

CSRF는 비활성화되어 있어 상태 변경 요청에 CSRF 토큰이 필요하지 않다. 쿠키 전송은 same-origin 전제다(개발 환경은 Vite proxy `/api` → 9090).

## 소유권

어템프트는 **로그인 사용자 또는 게스트 세션 중 정확히 하나**가 소유한다. 로그인하지 않은 상태로 어템프트 API(`/api/attempts/**`)나 `GET /api/me`를 호출하면 서버가 게스트 세션을 만들어 `Set-Cookie: GUEST_SESSION=...; HttpOnly`를 내려주고, 이후 그 쿠키가 소유자를 식별한다. 쿠키 원문은 저장되지 않고 SHA-256 해시만 보관된다.

- 게스트 세션의 기본 수명은 1시간이다(`GUEST_SESSION` 쿠키의 `Max-Age`). 만료되면 그 게스트가 만든 어템프트에는 더 이상 **쓰기**를 할 수 없다.
- **내 소유가 아닌 진행 중(`IN_PROGRESS`) 어템프트는 존재 여부를 알려주지 않고 404 `attempt-not-found`로 응답한다.** 403이 아니다 — ID를 무작위로 넣어 남의 풀이가 있는지 확인하는 것을 막기 위한 것이다.
- 게스트 상태로 풀던 어템프트는 **로그인 시점에 그 계정 소유로 이전된다.** 이전이 끝나면 게스트 쿠키는 만료된다.

즉 클라이언트는 로그인/비로그인 어느 쪽에서도 같은 어템프트 API를 그대로 호출하면 되고, 소유자 식별은 쿠키(세션 또는 게스트)로 자동 처리된다.

### 제출된 어템프트는 공개된다

**제출 완료(`SUBMITTED`)된 어템프트는 누구나 읽을 수 있다.** 랭킹이 모든 줄에 `attemptId`를 싣고, 그 ID로 남의 풀이와 피드백을 열어 보는 것이 랭킹의 쓸모이기 때문이다. 코드도 프롬프트도 가리지 않는다 — 사용자에게는 **제출이 곧 공개**라는 뜻이므로 제출 UI에서 그 사실을 알려야 한다.

| | 소유자 | 제3자(다른 사용자·게스트·익명) |
|---|---|---|
| 읽기 (`GET /{id}`, `/{id}/feedback`, `/{id}/runs`, `/{id}/runs/{runId}`) | 상태와 무관하게 200 | `SUBMITTED`면 200, `IN_PROGRESS`면 404 |
| 쓰기 (`POST /{id}/turns`, `/{id}/submit`, `/{id}/runs`, `/{id}/turns/{ordinal}/runs`) | 200/202 | 상태와 무관하게 404 |

- **제출은 쓰기를 열지 않는다.** 제출된 어템프트라도 턴 추가·재제출·코드 실행은 소유자만 할 수 있다. 남의 `attemptId`로 실행을 요청해 상대의 실행 슬롯을 막거나 워커를 돌리는 것을 막기 위한 것이다.
- 응답의 `mine`으로 내 풀이인지 판별한다. `false`면 클라이언트는 편집·제출 UI를 감추고 읽기 전용으로 그린다.
- 주인의 신원(사용자 ID·게스트 세션 ID)은 **어떤 응답에도 실리지 않는다.** 표시용 이름은 `ownerLabel`뿐이다.

## 공통 규칙

- **요청 Content-Type**: 바디가 있는 모든 요청은 `application/json`. 응답도 항상 `application/json`이며, 예외는 `POST /api/auth/logout`(204, 빈 본문)뿐이다.
- **모르는 JSON 키는 무시할 것**: 응답 스키마는 필드 추가 방식(additive)으로만 확장한다. 클라이언트는 문서에 없는 키가 보여도 실패하지 않아야 한다.
- **문자 인코딩**: UTF-8. 파일 내용·피드백에 한글과 개행이 그대로 들어간다.
- **날짜/시각**: ISO-8601 UTC 문자열 (예: `2026-07-29T05:00:00Z`).

### 공통 에러 응답 스키마

에러는 아래 두 필드로 통일된다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `code` | string | 기계가 분기할 오류 코드 (아래 표의 고정 문자열) |
| `message` | string | 사람이 읽는 한국어 설명. 문구는 변할 수 있으므로 분기에 쓰지 말 것 |

```json
{
  "code": "problem-not-found",
  "message": "문제 ID 999를 찾을 수 없습니다."
}
```

깨진 JSON·바디 누락(400 `invalid-request`), 없는 경로(404 `not-found`), 잘못된 메서드(405 `method-not-allowed`), 서버 내부 오류(500 `internal-server-error`)도 모두 이 형식으로 응답한다. 다만 역프록시나 컨테이너가 애플리케이션에 닿기 전에 끊는 응답(502·504·nginx 오류 페이지 등)은 이 형식이 아니므로, 클라이언트는 `code`가 없는 4xx/5xx도 처리할 수 있어야 한다.

### 전체 에러 코드표

| HTTP | code | 언제 발생 |
|---|---|---|
| 400 | `invalid-request` | 요청 바디 검증(`@Valid`) 실패 — 필수 누락·형식·길이 위반, 또는 `limit`이 허용 범위 밖 |
| 400 | `attempt-has-no-turns` | 턴이 하나도 없는 어템프트를 제출 |
| 401 | `bad-credentials` | 로그인 실패 (아이디 없음/비밀번호 불일치를 구분하지 않음) |
| 401 | `unauthenticated` | 세션 없이 인증 필요 경로 접근 (Security 필터가 차단) |
| 403 | `access-denied` | 인증됐으나 권한 부족 (현재 권한 등급이 하나라 사실상 미발생) |
| 404 | `not-found` | 존재하지 않는 경로 |
| 404 | `problem-not-found` | 해당 ID의 문제가 없음 |
| 404 | `attempt-not-found` | 해당 ID의 어템프트가 없음, **또는 접근 권한이 없음** (남의 진행 중 어템프트 읽기, 남의 어템프트 쓰기) |
| 404 | `turn-not-found` | 어템프트는 있으나 해당 번호의 턴이 없음 |
| 404 | `feedback-not-found` | 어템프트는 있으나 아직 제출 전이라 피드백이 없음 |
| 404 | `code-run-not-found` | 해당 어템프트에 그 실행 ID가 없음 |
| 405 | `method-not-allowed` | 경로는 있으나 지원하지 않는 HTTP 메서드 |
| 409 | `problem-inactive` | 비활성 문제로 새 어템프트를 시작하려 함 |
| 409 | `attempt-already-submitted` | 이미 제출된 어템프트에 턴을 추가하려 함 |
| 409 | `feedback-in-progress` | 해당 어템프트의 피드백 생성이 진행 중 |
| 409 | `code-run-in-progress` | 해당 어템프트에 아직 끝나지 않은 코드 실행이 있음 |
| 409 | `duplicate-request` | 같은 `Idempotency-Key`의 요청이 처리 중 |
| 409 | `duplicate-username` | 가입 시 아이디 중복 |
| 409 | `duplicate-email` | 가입 시 이메일 중복 |
| 409 | `duplicate-user` | 동시 가입 레이스에서 DB UNIQUE 제약에 걸린 안전망 |
| 502 | `ai-provider-error` | AI 제공자 호출 실패 (코드 생성·피드백 생성 공통) |
| 504 | `run-timeout` | AI 코드 생성 시간 초과 |
| 504 | `feedback-timeout` | AI 피드백 생성 시간 초과 |

### Idempotency-Key 규칙

`POST /api/attempts`와 `POST /api/attempts/{id}/turns`만 `Idempotency-Key` 요청 헤더를 받는다. 값은 클라이언트가 만든 임의 문자열이며 **선택**이다.

- 헤더가 없거나 값이 공백이면 멱등 처리를 하지 않는다(매번 새로 실행).
- 키를 처음 쓰면 서버가 그 키를 선점(PENDING)하고 요청을 실행한다. 성공하면 키에 결과 어템프트 ID를 기록(COMPLETED)한다.
- **같은 키로 다시 요청하면** AI를 재호출하지 않고, 그 키에 기록된 어템프트의 현재 상태를 그대로 반환한다. 상태 코드는 최초 요청과 같다(생성은 201, 턴 추가는 200).
- 선점된 키(PENDING)로 동시에 다른 요청이 오면 409 `duplicate-request`.
- 요청이 실패하면 키 기록이 삭제되어 **같은 키로 즉시 재시도**할 수 있다.
- PENDING이 7분을 넘기면 원 소유자가 죽은 것으로 보고 다른 요청이 키를 인수해 재실행한다.
- 키는 **엔드포인트·어템프트와 무관하게 전역**으로 조회된다. 어템프트 생성에 쓴 키를 턴 추가에 재사용하면 턴이 추가되지 않고 기록된 어템프트 상태만 돌아온다. 요청 1건당 새 키(UUID 등)를 쓸 것.

---

# 문제 (Problems)

### GET /api/problems

랜딩 화면에서 고를 수 있는 문제의 요약을 반환한다. 명세와 스켈레톤 파일은 빠진다. 비활성 문제는 목록에서 제외된다.

- **인증**: 불필요
- **파라미터**: 없음

**성공 응답 — 200**

| 필드 | 타입 | 설명 |
|---|---|---|
| `problems` | array | 문제 요약 목록 (비어 있을 수 있음) |
| `problems[].id` | number | 문제 ID |
| `problems[].title` | string | 문제 제목 |
| `problems[].type` | string | `coding` 또는 `game` |
| `problems[].language` | string | 채점 언어 `java` 또는 `python`. game 문제는 채점을 하지 않아 이 값을 쓰지 않는다(기본값 `java`가 그대로 남는다) |

```json
{
  "problems": [
    { "id": 1, "title": "Hello World 출력", "type": "coding", "language": "java" },
    { "id": 2, "title": "두 수의 합", "type": "coding", "language": "python" },
    { "id": 3, "title": "블럭 피하기 만들기", "type": "game", "language": "java" }
  ]
}
```

**에러**: 없음

### GET /api/problems/{id}

문제 명세(Markdown)와 스켈레톤 파일 전체를 반환한다. 비활성 문제도 단건 조회는 열려 있다(이미 시작한 어템프트를 계속 풀 수 있도록).

- **인증**: 불필요

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 문제 ID |

**성공 응답 — 200**

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number | 문제 ID |
| `title` | string | 문제 제목 |
| `specMd` | string | Markdown 형식의 문제 명세 |
| `type` | string | `coding` 또는 `game` |
| `files` | array | 문제가 제공하는 스켈레톤 파일 목록 |
| `files[].path` | string | 파일 경로 |
| `files[].content` | string | 파일 내용 |

```json
{
  "id": 1,
  "title": "Hello World 출력",
  "specMd": "# Hello World 출력\n\n표준 출력으로 `Hello, World!`를 출력하세요.\n",
  "type": "coding",
  "files": [
    {
      "path": "src/main/java/Main.java",
      "content": "class Main {\n    public static void main(String[] args) {\n    }\n}\n"
    }
  ]
}
```

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 404 | `problem-not-found` | 해당 ID의 문제가 없음 |

---

# 랭킹 (Ranking)

문제 하나를 **누가 가장 적은 비용으로 풀었는지** 보여준다. 조회만 있고 공개다.

## 무엇을 재는가

- **비용은 저장된 값이 아니다.** `attempt_llm_call`에 남는 비용은 쓰기 시점 단가로 박힌 지출 기록이라, 단가가 한 번 바뀌면 과거 제출과 새 제출이 서로 다른 자로 재어진다. 랭킹은 **현재 단가로 전원을 다시 잰다.**
- **턴에 속한 LLM 호출만 센다.** 제출 시 도는 피드백 생성 호출은 사용자의 프롬프트 실력과 무관하므로 빠진다.
- **단위는 어템프트 1건 = 1줄이다.** 한 사람이 여러 번 제출했으면 여러 줄을 차지한다.

## 랭킹에 드는 조건

다섯을 모두 만족해야 표에 든다.

1. **로그인 사용자의 제출일 것** — 게스트는 상위 목록에도, 등수에도, `totalCount`에도, `myBest`에도 나오지 않는다. 막으려는 것은 게스트가 쓴 프롬프트가 아니라 이름 없는 줄이 표에 섞이는 것이다. 게스트가 이미 푼 기록은 **로그인하면 소급 등재된다** — 로그인 시점에 그 어템프트의 소유자가 계정으로 이전되기 때문이다.
2. 제출이 완료된 어템프트(`SUBMITTED`)일 것.
3. **마지막 턴의 코드가 채점을 통과**했을 것 — 그 턴을 대상으로 한 `SUCCEEDED` 코드 실행이 있어야 한다. 이 조건이 없으면 아무것도 만들지 않은 빈 프롬프트가 비용 0으로 1등을 한다.
4. 턴에 속한 LLM 호출이 하나 이상일 것 — 사용량 기록 도입 이전 어템프트는 비용이 0인 게 아니라 **모르는** 것이라 뺀다.
5. 그 호출이 **전부** 비용을 계산할 수 있을 것 — 하나라도 토큰이나 모델 단가를 모르면 합계가 거짓말이 되므로 그 어템프트를 통째로 뺀다.

## 등수와 정렬

- 등수는 비용만 본다. **동점은 같은 등수를 받고 다음 등수는 건너뛴다**(1, 1, 3).
- 표에 찍히는 순서는 `비용 오름차순 → 제출 시각 빠른 순(모르면 맨 뒤) → 어템프트 ID 순`으로 완전히 결정적이다.

### GET /api/problems/{id}/ranking

- **인증**: 불필요. 다만 로그인 세션을 함께 보내야 `myBest`가 채워진다. **이 경로는 게스트 쿠키를 아예 보지 않는다** — 게스트는 랭킹에 들지 않으므로 볼 이유가 없고, 덕분에 랭킹만 구경한 방문자에게 쿠키와 DB 행이 생기지 않는다.

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 문제 ID. 비활성 문제도 조회된다 |

| 쿼리 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `limit` | number | 선택 | 가져올 줄 수. 기본 10, 허용 범위 1~50 |

**성공 응답 — 200**

| 필드 | 타입 | 설명 |
|---|---|---|
| `problemId` | number | 문제 ID |
| `totalCount` | number | 랭킹에 든 제출 **전체** 수. `entries`는 그중 상위 일부다 |
| `entries` | array | 등수 순 상위 목록. 자격을 갖춘 제출이 없으면 `[]` |
| `myBest` | object \| null | 요청자의 가장 좋은 줄. 자격을 갖춘 내 어템프트가 없으면 `null` |

`entries[]`와 `myBest`는 같은 스키마다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `rank` | number | 등수. 동점은 같은 값 |
| `attemptId` | number | 이 줄의 어템프트 ID. **모든 줄에 실린다** — 랭킹에서 해당 풀이·피드백 화면으로 이동할 때 쓴다. 랭킹에 오른 어템프트는 모두 제출 완료라 [누구나 읽을 수 있다](#제출된-어템프트는-공개된다) |
| `mine` | boolean | 요청자 본인의 줄인지. 어템프트 1건이 1줄이라 **내 줄이 여럿일 수 있다** |
| `ownerLabel` | string | 주인의 닉네임. 랭킹에는 로그인 사용자의 제출만 들어오므로 항상 채워진다 |
| `cost` | number | 현재 단가로 다시 잰 비용(USD, 소수점 8자리) |
| `uncachedInputTokens` | number | 캐시에 걸리지 않은 입력 토큰 합 |
| `cachedInputTokens` | number | 캐시에 걸린 입력 토큰 합 |
| `outputTokens` | number | 출력 토큰 합 |
| `turns` | number | 턴 수 |
| `rounds` | number | LLM 호출 수. 턴 하나가 여러 라운드를 쓸 수 있다 |
| `submittedAt` | string \| null | 제출 시각(ISO-8601). 오래된 기록은 null일 수 있다 |
| `durationSeconds` | number \| null | 소요 시간(초) = 제출 시각 − 첫 CODE 호출 시각. 실패한 코드 생성 호출도 시작으로 친다. `submittedAt`이 null인 옛 기록은 null |

**`myBest`는 상위 목록에 이미 있어도 항상 채운다.** 중복해서 그릴지 여부는 `myBest.attemptId`와 같은 `attemptId`가 `entries`에 있는지로 클라이언트가 판단한다 — `mine`으로는 판단할 수 없다. 내 줄이 상위에 여럿 올라오면 그중 하나만 `myBest`이기 때문이다.

```json
{
  "problemId": 3,
  "totalCount": 37,
  "entries": [
    {
      "rank": 1,
      "attemptId": 88,
      "mine": false,
      "ownerLabel": "프롬프트왕",
      "cost": 0.00300000,
      "uncachedInputTokens": 1500,
      "cachedInputTokens": 400,
      "outputTokens": 500,
      "turns": 1,
      "rounds": 2,
      "submittedAt": "2026-08-03T05:10:32Z",
      "durationSeconds": 252
    }
  ],
  "myBest": {
    "rank": 12,
    "attemptId": 42,
    "mine": true,
    "ownerLabel": "나",
    "cost": 0.00900000,
    "uncachedInputTokens": 4500,
    "cachedInputTokens": 1200,
    "outputTokens": 1500,
    "turns": 3,
    "rounds": 6,
    "submittedAt": "2026-08-03T06:22:10Z",
    "durationSeconds": 4980
  }
}
```

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 400 | `invalid-request` | `limit`이 정수가 아니거나 1~50을 벗어남 |
| 404 | `problem-not-found` | 해당 ID의 문제가 없음 |

---

# 어템프트 (Attempts)

## 상태 전이

어템프트 상태(`status`)는 `IN_PROGRESS`와 `SUBMITTED` 두 값뿐이며, 전이는 `IN_PROGRESS → SUBMITTED` 한 방향이다(제출 후 되돌릴 수 없다).

| 호출 | IN_PROGRESS | SUBMITTED |
|---|---|---|
| `GET /api/attempts/{id}` | 200 | 200 |
| `POST /api/attempts/{id}/turns` | 200 (턴 추가) | 409 `attempt-already-submitted` |
| `POST /api/attempts/{id}/submit` | 200 (피드백 생성 후 SUBMITTED) | 200 (저장된 피드백 재반환, AI 재호출 없음) |
| `GET /api/attempts/{id}/feedback` | 404 `feedback-not-found` | 200 |

턴이 0개인 상태에서 제출하면 상태는 그대로 `IN_PROGRESS`이고 400 `attempt-has-no-turns`가 난다.

위 표는 **소유자**가 호출했을 때다. 제3자는 `SUBMITTED`만 읽을 수 있고 쓰기는 어느 상태에서도 404다 — [제출된 어템프트는 공개된다](#제출된-어템프트는-공개된다)를 볼 것.

## AttemptResponse 공통 스키마

`POST /api/attempts`, `GET /api/attempts/{id}`, `POST /api/attempts/{id}/turns`가 모두 이 형태를 반환한다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number | 어템프트 ID |
| `problemId` | number | 풀이 중인 문제 ID |
| `baseFiles` | array | 어템프트를 시작한 문제 스켈레톤 파일 전체 (불변) |
| `baseFiles[].path` | string | 파일 경로 |
| `baseFiles[].content` | string | 파일 내용 |
| `files` | array | 현재 프로젝트 파일 전체 (`baseFiles`에 모든 턴의 변경을 적용한 결과) |
| `files[].path` | string | 파일 경로 |
| `files[].content` | string | 파일 내용 |
| `turns` | array | 지금까지 진행한 턴 목록. 배열 순서가 진행 순서다 (인덱스 0 = 1번째 턴) |
| `turns[].prompt` | string | 사용자가 보낸 작업 요청 |
| `turns[].aiResponse` | string | AI의 작업 요약 |
| `turns[].changedFiles` | array | 직전 상태 대비 변경 파일 목록 |
| `turns[].changedFiles[].path` | string | 변경된 파일 경로 |
| `turns[].changedFiles[].changeType` | string | `ADDED` \| `MODIFIED` \| `DELETED` |
| `turns[].changedFiles[].content` | string \| null | 변경 후 파일 전체 내용. `DELETED`는 null |
| `turns[].toolCalls` | array | 해당 턴에서 AI가 호출한 툴 기록 |
| `turns[].toolCalls[].tool` | string | `list_files` \| `read_file` \| `edit_file` |
| `turns[].toolCalls[].path` | string \| null | 대상 파일 경로. 대상이 없는 툴(`list_files`)은 null |
| `turns[].usage` | object \| null | 그 턴의 LLM 사용량 합계. 사용량 기록 도입 이전 턴은 null |
| `turns[].usage.inputTokens` | number \| null | 입력 토큰 합계. 캐시 적중분을 포함한 전체 |
| `turns[].usage.uncachedInputTokens` | number \| null | 캐시가 안 먹은 입력 토큰 합계(= `inputTokens` − `cachedInputTokens`) |
| `turns[].usage.cachedInputTokens` | number \| null | 캐시 적중 입력 토큰 합계. `inputTokens`에 포함된 값 |
| `turns[].usage.outputTokens` | number \| null | 출력 토큰 합계 |
| `turns[].usage.reasoningTokens` | number \| null | 추론 토큰 합계. `outputTokens`에 포함된 값 |
| `turns[].usage.latencyMs` | number \| null | LLM 호출 왕복 시간 합(ms) |
| `turns[].usage.cost` | number \| null | USD 비용. 단가가 등록되지 않은 모델은 null |
| `turns[].usage.model` | string \| null | 그 턴의 호출에 쓴 모델 |
| `turns[].usage.rounds` | number | 그 턴의 LLM 호출 횟수 |
| `status` | string | `IN_PROGRESS` \| `SUBMITTED` |
| `ownerLabel` | string \| null | 주인의 표시 이름. 로그인 사용자는 닉네임, 게스트는 세션 UUID 앞 네 자다(`게스트` 같은 접두어는 클라이언트가 붙인다). **`POST /api/attempts`(생성) 응답에서만 항상 null이다** — 아래 주의를 볼 것 |
| `mine` | boolean | 요청자 본인의 어템프트인지. `false`면 남의 풀이를 읽고 있는 것이므로 편집·제출 UI를 감춘다 |
| `usage` | object \| null | 어템프트의 LLM 사용량 총계(= 턴별 합계의 합). 턴 사용량 기록이 없으면 null |
| `usage.inputTokens` | number \| null | 입력 토큰 총계. 캐시 적중분을 포함한 전체 |
| `usage.uncachedInputTokens` | number \| null | 캐시가 안 먹은 입력 토큰 총계 |
| `usage.cachedInputTokens` | number \| null | 캐시 적중 입력 토큰 총계. `inputTokens`에 포함된 값 |
| `usage.outputTokens` | number \| null | 출력 토큰 총계 |
| `usage.reasoningTokens` | number \| null | 추론 토큰 총계. `outputTokens`에 포함된 값 |
| `usage.latencyMs` | number \| null | LLM 호출 왕복 시간 합(ms) |
| `usage.cost` | number \| null | USD 비용 총계. 단가가 등록되지 않은 모델의 호출은 빠진다 |
| `usage.rounds` | number | 어템프트의 턴들이 낸 LLM 호출 횟수 합 |

이 응답에 피드백은 포함되지 않는다. 피드백은 `GET /api/attempts/{id}/feedback` 또는 제출 응답으로만 받는다.

> **생성 응답에는 `ownerLabel`이 없다.** `POST /api/attempts`는 방금 쓴 엔티티로 응답을 만드는데 그 경로는 닉네임을 조인하지 않으므로, 로그인 사용자의 어템프트라도 `ownerLabel`이 null로 나간다. `POST /api/attempts/{id}/turns`는 사용량을 얻으려 커밋 후 다시 읽으므로 값이 채워진다. `mine`은 두 경로 모두 정확하다(요청자가 곧 주인이므로 항상 `true`). 생성 직후 주인 이름을 그려야 하면 `GET /api/attempts/{id}`의 값을 쓴다.

사용량 계약:

- **`turns[].usage`를 모두 더하면 `usage`가 된다.** 턴에 속하지 않는 호출(제출 시 피드백 생성, 실패로 턴이 저장되지 않은 호출)은 총계에서 뺀다 — 이 수치는 사용자가 자기 프롬프트의 효율을 보는 지표이고, 그 호출들은 서비스가 부담하는 비용이기 때문이다. 그래서 제출 전후로 `usage`가 달라지지 않는다.
- **사용량 기록 도입 이전 데이터는 `usage`가 null이다.** 기존 데이터를 마이그레이션하지 않으므로 클라이언트는 null을 정상 케이스로 처리해야 한다.
- **토큰 항목에는 포함 관계가 있다.** `cachedInputTokens`는 `inputTokens`의 일부이고, `reasoningTokens`는 `outputTokens`의 일부다. 나란히 더하면 이중 계산이 된다. 캐시 적중분과 신규 입력분은 단가가 10배 가까이 차이 나므로, 비용 구조를 보여줄 때는 `uncachedInputTokens`와 `cachedInputTokens`를 쓴다. 다만 `uncachedInputTokens`는 호출마다 입력에서 캐시 적중분을 뺀 값을 더한 것이라, **입력 토큰을 모르는 호출이 섞이면 `cachedInputTokens`와 더해도 `inputTokens`가 되지 않는다.** 표시할 값은 계산하지 말고 각각 그대로 쓴다.
- `latencyMs`는 **LLM 호출의 왕복 시간 합**이다. 툴 실행·응답 파싱·저장에 든 시간은 들어가지 않으므로 사용자가 실제로 기다린 시간보다 짧다.
- `cost`는 호출 시점 단가로 계산해 저장한 USD 값이다. 단가가 등록되지 않은 모델은 0이 아니라 null이며, 그런 호출은 합계에서 빠진다.
- **제공자가 사용량을 주지 않은 호출은 해당 항목의 합계에서 빠진다.** 0으로 세지 않는다 — 모르는 값을 0으로 적으면 합계가 거짓말이 되기 때문이다. 그런 호출도 `rounds`와 `latencyMs`에는 잡힌다.
- 이 봉투는 필드 추가 방식으로만 확장한다. **클라이언트는 모르는 JSON 키를 무시해야 한다.**

턴이 있는 어템프트의 전체 예시:

```json
{
  "id": 1,
  "problemId": 1,
  "baseFiles": [
    {
      "path": "src/main/java/Main.java",
      "content": "class Main {\n    public static void main(String[] args) {\n    }\n}\n"
    }
  ],
  "files": [
    {
      "path": "src/main/java/Main.java",
      "content": "class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}\n"
    }
  ],
  "turns": [
    {
      "prompt": "Hello, World!를 출력하도록 코드를 완성해줘",
      "aiResponse": "Main.java의 main 메서드에 표준 출력 한 줄을 추가했습니다.",
      "changedFiles": [
        {
          "path": "src/main/java/Main.java",
          "changeType": "MODIFIED",
          "content": "class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, World!\");\n    }\n}\n"
        }
      ],
      "toolCalls": [
        { "tool": "list_files", "path": null },
        { "tool": "read_file", "path": "src/main/java/Main.java" },
        { "tool": "edit_file", "path": "src/main/java/Main.java" }
      ],
      "usage": {
        "inputTokens": 2500,
        "uncachedInputTokens": 1500,
        "cachedInputTokens": 1000,
        "outputTokens": 500,
        "reasoningTokens": 120,
        "latencyMs": 260,
        "cost": 0.00300000,
        "model": "gpt-5.6-luna",
        "rounds": 2
      }
    }
  ],
  "status": "IN_PROGRESS",
  "ownerLabel": "프롬프트왕",
  "mine": true,
  "usage": {
    "inputTokens": 2500,
    "uncachedInputTokens": 1500,
    "cachedInputTokens": 1000,
    "outputTokens": 500,
    "reasoningTokens": 120,
    "latencyMs": 260,
    "cost": 0.00300000,
    "rounds": 2
  }
}
```

### POST /api/attempts

문제 스켈레톤 파일로 초기화된 새 어템프트를 생성한다.

- **인증**: 불필요

**요청 헤더**

| 헤더 | 필수 | 설명 |
|---|---|---|
| `Content-Type` | 필수 | `application/json` |
| `Idempotency-Key` | 선택 | 중복 요청 방지 키. 같은 키로 다시 요청하면 새로 만들지 않고 기존 어템프트를 반환한다 (위 [Idempotency-Key 규칙](#idempotency-key-규칙) 참조) |

**요청 바디**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `problemId` | number | 필수 | null 불가 | 풀이를 시작할 문제 ID |

```json
{ "problemId": 1 }
```

**성공 응답 — 201 Created**

스키마는 [AttemptResponse](#attemptresponse-공통-스키마)와 같다. 생성 직후에는 `turns`가 빈 배열이고 `files`가 `baseFiles`와 동일하며 `status`는 `IN_PROGRESS`다. 같은 `Idempotency-Key`로 재요청한 replay 응답도 201로 온다.

```json
{
  "id": 1,
  "problemId": 1,
  "baseFiles": [
    {
      "path": "src/main/java/Main.java",
      "content": "class Main {\n    public static void main(String[] args) {\n    }\n}\n"
    }
  ],
  "files": [
    {
      "path": "src/main/java/Main.java",
      "content": "class Main {\n    public static void main(String[] args) {\n    }\n}\n"
    }
  ],
  "turns": [],
  "status": "IN_PROGRESS",
  "ownerLabel": null,
  "mine": true,
  "usage": null
}
```

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 400 | `invalid-request` | `problemId` 누락 |
| 404 | `problem-not-found` | 해당 ID의 문제가 없음 |
| 409 | `problem-inactive` | 비활성 문제 — 새로 시작할 수 없음 |
| 409 | `duplicate-request` | 같은 `Idempotency-Key`의 요청이 처리 중 |

### GET /api/attempts/{id}

어템프트의 현재 파일 전체와 턴 기록을 반환한다.

- **인증**: 불필요. **제출 완료된 어템프트는 누구나, 진행 중이면 소유자만** 조회할 수 있다 ([자세히](#제출된-어템프트는-공개된다)).

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 어템프트 ID |

**성공 응답 — 200**: [AttemptResponse](#attemptresponse-공통-스키마)

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 404 | `attempt-not-found` | 해당 ID의 어템프트가 없음, 또는 **남의 진행 중 어템프트** |

### POST /api/attempts/{id}/turns

이전 대화 이력과 현재 파일을 AI에 전달해 코드를 갱신하고, 갱신된 어템프트 상태를 반환한다. AI 호출이 포함되므로 응답이 수십 초 이상 걸릴 수 있다(서버 측 코드 생성 상한 5분, 초과 시 504 `run-timeout`).

- **인증**: 불필요

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 어템프트 ID |

**요청 헤더**

| 헤더 | 필수 | 설명 |
|---|---|---|
| `Content-Type` | 필수 | `application/json` |
| `Idempotency-Key` | 선택 | 중복 요청 방지 키. 같은 키로 다시 요청하면 AI를 재호출하지 않고 기존 결과를 반환한다 |

**요청 바디**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `prompt` | string | 필수 | 공백만은 불가 | AI에게 전달할 작업 요청 |

```json
{ "prompt": "Hello, World!를 출력하도록 코드를 완성해줘" }
```

**성공 응답 — 200**: [AttemptResponse](#attemptresponse-공통-스키마). `turns` 배열 끝에 이번 턴이 추가되고 `files`와 `usage`가 갱신된다.

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 400 | `invalid-request` | `prompt`가 없거나 공백만 |
| 404 | `attempt-not-found` | 해당 ID의 어템프트가 없거나 **내 소유가 아님** (제출 완료 여부와 무관하다) |
| 409 | `duplicate-request` | 같은 `Idempotency-Key`의 요청이 처리 중 |
| 409 | `feedback-in-progress` | 이 어템프트의 피드백 생성이 진행 중 |
| 409 | `attempt-already-submitted` | 이미 제출된 어템프트 |
| 502 | `ai-provider-error` | AI 제공자 호출 실패 |
| 504 | `run-timeout` | AI 코드 생성 시간 초과 |

502/504로 실패한 경우 `Idempotency-Key` 기록은 삭제되므로 같은 키로 재시도할 수 있다.

### POST /api/attempts/{id}/submit

문제 명세와 어템프트의 전체 턴 기록을 바탕으로 턴별 피드백과 세션 전체 피드백을 생성해 저장하고 어템프트를 종료한다(`status` → `SUBMITTED`). 프롬프트 렌즈와 작업 방식(pattern) 렌즈를 **동시에 두 번** 호출하고, 둘 다 성공해야 제출이 완료된다. 이미 제출된 어템프트를 다시 제출하면 AI를 재호출하지 않고 저장된 피드백을 그대로 반환한다. AI 호출이 포함되므로 응답이 오래 걸릴 수 있다(호출 하나당 상한 5분이고 응답 형태가 어긋나면 각 호출이 한 번씩 재시도하므로 최악은 그보다 길다. 초과 시 504 `feedback-timeout`).

- **인증**: 불필요. **소유자만** 제출할 수 있다.
- **요청 바디**: 없음
- **`Idempotency-Key`**: 지원하지 않는다. 재제출 자체가 멱등이다.
- **제출하면 그 풀이와 피드백이 공개된다** — 랭킹을 통해 누구나 읽을 수 있다([자세히](#제출된-어템프트는-공개된다)). 클라이언트는 제출 전에 이 사실을 알려야 한다.

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 어템프트 ID |

**성공 응답 — 200**: [FeedbackResponse](#feedbackresponse-계약)

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 400 | `attempt-has-no-turns` | 턴이 하나도 없는 어템프트 |
| 404 | `attempt-not-found` | 해당 ID의 어템프트가 없거나 **내 소유가 아님** |
| 409 | `feedback-in-progress` | 같은 어템프트의 제출이 이미 진행 중 |
| 502 | `ai-provider-error` | AI 제공자 호출 실패 |
| 504 | `feedback-timeout` | 피드백 생성 시간 초과 |

---

# 코드 빌드/실행 (Code Runs)

어템프트의 코드를 컴파일해 실행하고, 문제에 채점용 테스트가 등록되어 있으면 그 테스트로 채점한다.

**비동기다.** 실행 요청은 즉시 202로 실행 ID(`runId`)만 반환하고, 실제 실행은 별도 워커에서 일어난다. 클라이언트는 조회 API를 폴링해 결과를 확인한다.

```
POST .../runs  ──▶ 실행 접수(QUEUED) ──▶ 큐 ──▶ 워커(javac + JUnit)
                                                    │
GET .../runs/{runId} ◀── 결과 반영 ◀── 큐 ◀─────────┘
```

## 실행 방식

문제에 채점 테스트가 있으면 JUnit으로, 없으면 `main`을 실행한다. **채점 테스트는 어떤 응답에도 포함되지 않는다** — 문제 조회의 `files`에도 어템프트의 `baseFiles`에도 없고, 실행 시점에 서버가 워커로 직접 전달한다.

| | 테스트 있음 | 테스트 없음 |
|---|---|---|
| 실행 대상 | JUnit 콘솔 런처로 전체 테스트 | `Main` 클래스의 `main()` |
| 타임아웃 | 30초 | 10초 |
| 통과 판정 | 테스트 전원 통과 | 종료 코드 0 |

- 컴파일 타임아웃은 30초다.
- `stdout`·`stderr`는 각각 **64KB에서 절단**되며, 절단되면 끝에 안내 문구가 붙는다. **앞쪽부터 보관하므로 제출 코드가 출력을 많이 하면 뒤에 오는 JUnit 결과가 잘려 나갈 수 있다.**
- 실행 환경은 힙 256MB, CPU 1개, 환경변수 없음, 실행마다 새 임시 디렉터리다.

## 상태 값

| status | 종료? | 의미 |
|---|---|---|
| `QUEUED` | ✗ | 큐 대기 또는 실행 중. **이 값만 진행 중이다** |
| `SUCCEEDED` | ✓ | 테스트 전원 통과(테스트 있음) 또는 정상 종료(테스트 없음) |
| `TEST_FAILED` | ✓ | 컴파일·실행은 됐지만 채점 테스트가 깨졌다. **사용자가 고쳐야 하는 유일한 실패** |
| `COMPILE_ERROR` | ✓ | 컴파일 실패. `stderr`에 오류. 실행할 `.java`가 없을 때도 이 값 |
| `RUNTIME_ERROR` | ✓ | 0이 아닌 코드로 종료, 또는 `main` 메서드를 못 찾음 |
| `TIMEOUT` | ✓ | 위 타임아웃 초과. `exitCode`는 null |
| `RUNNER_ERROR` | ✓ | 워커 장애, 잘못된 파일 경로, 좌초된 실행 회수 |

## 동시성·수명 규칙

1. **어템프트당 미완료 실행은 1건**이다. 진행 중에 새로 요청하면 409 `code-run-in-progress`.
2. `QUEUED`가 **2분**을 넘기면 워커가 죽은 것으로 보고 `RUNNER_ERROR`로 회수한다. 회수는 실행 요청·조회 시점에 함께 수행되므로, 409에 갇혔다면 폴링을 이어가면 풀린다.
3. 결과 반영은 멱등하다. 회수된 뒤 늦게 도착한 결과나 재전달된 메시지는 무시된다.
4. **채점 테스트는 실행 시점의 것을 쓴다.** 문제 저장소가 재동기화되어 테스트가 바뀌면 같은 턴을 다시 실행해도 결과가 달라질 수 있다 — 코드만 불변이다.

### POST /api/attempts/{id}/runs

**마지막 턴**의 코드를 실행한다. 턴이 하나도 없으면 시작 스켈레톤을 실행하고 `turnOrdinal`은 null이 된다.

- **인증**: 불필요. **소유자만** 요청할 수 있다(제출 완료 여부와 무관하다).
- **요청 바디**: 없음
- **`Idempotency-Key`**: 지원하지 않는다

**성공 응답 — 202 Accepted**: [CodeRunResponse](#coderunresponse-스키마)

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 404 | `attempt-not-found` | 어템프트가 없거나 내 소유가 아님 |
| 409 | `code-run-in-progress` | 이 어템프트에 아직 끝나지 않은 실행이 있음 |

### POST /api/attempts/{id}/turns/{ordinal}/runs

**지정한 턴 시점**의 코드를 실행한다. 턴은 불변이므로 과거 턴을 다시 실행하면 그때의 코드가 그대로 실행된다 — "몇 번째 프롬프트까지 통과했는지"를 확인하는 용도다.

- **인증**: 불필요. **소유자만** 요청할 수 있다(제출 완료 여부와 무관하다). 랭킹에서 얻은 남의 `attemptId`로는 404다.

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 어템프트 ID |
| `ordinal` | number | 필수 | 실행할 턴 번호. **0부터 시작**한다 (`AttemptResponse.turns` 배열 인덱스와 같다) |

> 피드백의 `turns[].turn`은 1부터 시작하지만 이 `ordinal`은 **0부터**다. 혼동하지 말 것.

**성공 응답 — 202 Accepted**: [CodeRunResponse](#coderunresponse-스키마). `turnOrdinal`은 요청한 값 그대로다.

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 404 | `attempt-not-found` | 어템프트가 없거나 내 소유가 아님 |
| 404 | `turn-not-found` | `ordinal`이 0보다 작거나 턴 개수 이상 |
| 409 | `code-run-in-progress` | 이 어템프트에 아직 끝나지 않은 실행이 있음 |

### GET /api/attempts/{id}/runs

이 어템프트에서 지금까지 요청한 실행을 **최근 순으로 전부** 반환한다.

실행 ID를 클라이언트가 보관하지 않아도 되게 하는 것이 이 API의 목적이다. 새로고침 후 진행 중인 실행을 이어서 폴링하거나, 턴별 통과 여부를 표시하거나, 409를 받았을 때 무엇이 진행 중인지 확인할 때 쓴다.

- **인증**: 불필요. **제출 완료된 어템프트는 누구나, 진행 중이면 소유자만** 조회할 수 있다 ([자세히](#제출된-어템프트는-공개된다)).
- **파라미터**: 없음

**성공 응답 — 200**

| 필드 | 타입 | 설명 |
|---|---|---|
| `runs` | array | 실행 목록. 최근 실행이 먼저 온다. 실행이 없으면 빈 배열 |
| `runs[].runId` | string | 실행 ID (UUID) |
| `runs[].turnOrdinal` | number \| null | 실행한 코드가 몇 번째 턴의 것인지(0-based). 스켈레톤 실행이거나 턴 단위 기록 이전이면 null |
| `runs[].status` | string | 위 [상태 값](#상태-값) |
| `runs[].exitCode` | number \| null | 종료 코드. 종료 전이거나 타임아웃이면 null |
| `runs[].durationMs` | number \| null | 소요 시간(ms). 종료 전에는 null |
| `runs[].createdAt` | string | 실행을 접수한 시각 (ISO-8601) |
| `runs[].finishedAt` | string \| null | 실행이 끝난 시각. 아직 `QUEUED`면 null |

**`stdout`·`stderr`는 목록에 포함되지 않는다.** 두 값은 각각 64KB까지 커질 수 있어 목록에 실으면 응답이 지나치게 무거워진다. 본문이 필요하면 `runId`로 단건 조회한다.

```json
{
  "runs": [
    {
      "runId": "630f4bb3-67e7-4ca4-8ab9-75762d897bdf",
      "turnOrdinal": 1,
      "status": "QUEUED",
      "exitCode": null,
      "durationMs": null,
      "createdAt": "2026-08-03T06:35:34.060670Z",
      "finishedAt": null
    },
    {
      "runId": "05707367-5c71-4fe4-bcea-e943c9b6f077",
      "turnOrdinal": 0,
      "status": "TEST_FAILED",
      "exitCode": 1,
      "durationMs": 1224,
      "createdAt": "2026-08-03T06:35:05.554402Z",
      "finishedAt": "2026-08-03T06:35:06.788055Z"
    }
  ]
}
```

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 404 | `attempt-not-found` | 어템프트가 없음, 또는 **남의 진행 중 어템프트** |

### GET /api/attempts/{id}/runs/{runId}

실행 하나의 상태와 결과 본문을 반환한다. 폴링에 쓴다.

- **인증**: 불필요. **제출 완료된 어템프트는 누구나, 진행 중이면 소유자만** 조회할 수 있다 ([자세히](#제출된-어템프트는-공개된다)).

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 어템프트 ID |
| `runId` | string | 필수 | 실행 ID (UUID) |

**성공 응답 — 200**: [CodeRunResponse](#coderunresponse-스키마)

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 404 | `attempt-not-found` | 어템프트가 없음, 또는 **남의 진행 중 어템프트** |
| 404 | `code-run-not-found` | **그 어템프트에** 해당 실행 ID가 없음 (다른 어템프트의 `runId`로는 조회되지 않는다) |

## CodeRunResponse 스키마

실행 요청(202)과 단건 조회(200)가 같은 형태를 반환한다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `runId` | string | 실행 ID (UUID) |
| `turnOrdinal` | number \| null | 실행한 코드가 몇 번째 턴의 것인지(0-based) |
| `status` | string | 위 [상태 값](#상태-값) |
| `exitCode` | number \| null | 종료 코드 |
| `stdout` | string \| null | 표준 출력. **JUnit 실행 결과가 여기 담긴다** |
| `stderr` | string \| null | 표준 에러. **컴파일 오류 메시지가 여기 담긴다** |
| `durationMs` | number \| null | 소요 시간(ms) |

미완료(`QUEUED`) 상태에서는 `exitCode`·`stdout`·`stderr`·`durationMs`가 모두 **키는 있고 값이 `null`이다**(키가 생략되지 않는다).

```json
{
  "runId": "630f4bb3-67e7-4ca4-8ab9-75762d897bdf",
  "turnOrdinal": 0,
  "status": "QUEUED",
  "exitCode": null,
  "stdout": null,
  "stderr": null,
  "durationMs": null
}
```

`stdout`에는 JUnit 콘솔 런처의 출력이 그대로 들어온다. 구조화된 케이스별 결과는 아직 제공하지 않으므로, 클라이언트는 이 문자열을 그대로 보여주는 것을 전제로 한다.

```
├─ PhoneNumberFormatterTest ✔
│  ├─ 휴대전화_번호를_마스킹한다() ✔
│  ├─ 지역번호_2자리와_국번_3자리를_마스킹한다() ✘ expected: <02-***-4567> but was: <02-1****567>
[         5 tests successful      ]
[         3 tests failed          ]
```

## 권장 폴링 절차

```
1) POST .../runs                     → 202, runId 획득
   409 code-run-in-progress 이면 → GET .../runs 로 진행 중인 실행을 찾아 그 runId로 이어감
2) GET .../runs/{runId} 반복 (1~2초 간격)
3) status !== 'QUEUED' 이면 종료. 최대 대기는 2분(회수 TTL)으로 잡으면 충분하다
```

화면에 처음 들어올 때는 `GET .../runs`를 먼저 호출해 상태를 복원한다. 목록 첫 항목이 `QUEUED`면 그 `runId`로 폴링을 이어가면 된다.

---

# 피드백 (Feedback)

## FeedbackResponse 계약

`POST /api/attempts/{id}/submit`과 `GET /api/attempts/{id}/feedback`이 같은 형태를 반환한다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `turns` | array | 턴별 피드백 |
| `turns[].turn` | number | 턴 번호. **1부터 시작**한다 |
| `turns[].feedbackMd` | string | 해당 턴 프롬프트에 대한 피드백 (**Markdown**) |
| `turns[].patternMd` | string \| null | 해당 턴의 **작업 방식**에 이름을 붙인 피드백 (**Markdown**) |
| `overallMd` | string | 세션 전체에 대한 피드백 (**Markdown**) |
| `patternOverallMd` | string \| null | 세션 전체의 **작업 방식**에 대한 피드백 (**Markdown**) |

`feedbackMd`/`overallMd`와 `patternMd`/`patternOverallMd`는 같은 세션을 다른 렌즈로 본 결과다. 앞은 프롬프트가
무엇을 전달했는지 짚고, 뒤는 사용자가 어떻게 일했는지에 이름을 붙인다. 한 화면에 나란히 렌더하는 것을 의도한다.

계약 사항:

- `turns[].turn`은 **1부터** 매겨진다. `AttemptResponse.turns` 배열의 인덱스 `n`은 `turn = n + 1`에 대응한다.
- `feedbackMd`·`overallMd`·`patternMd`·`patternOverallMd`는 모두 **Markdown 문자열**이다. 클라이언트가 Markdown으로 렌더해야 한다.
- **턴별 피드백 도입 이전에 제출된 어템프트는 `turns`가 빈 배열(`[]`)이다.** 이때는 `overallMd`만 렌더한다. 즉 `turns.length === 0`인 경우를 정상 케이스로 처리해야 한다.
- **pattern 피드백 도입 이전에 제출된 어템프트는 `turns[].patternMd`와 `patternOverallMd`가 `null`이다.** 이때는 프롬프트 피드백만 렌더한다. 기존 데이터를 마이그레이션하지 않는다.
- 두 pattern 필드는 **함께 채워지거나 함께 `null`이다.** 제출은 두 렌즈를 모두 성공시켜야 완료되므로 한쪽만 있는 상태는 생기지 않는다.
- 턴별 피드백이 있는 경우 `turns`의 길이는 어템프트의 턴 수와 같다(전부 배정되거나 하나도 배정되지 않는다).
- 이 봉투는 필드 추가 방식으로만 확장한다. **클라이언트는 모르는 JSON 키를 무시해야 한다.**

```json
{
  "turns": [
    {
      "turn": 1,
      "feedbackMd": "## 좋은 점\n출력 형식을 명확히 지정했습니다.\n\n## 개선점\n어떤 파일을 수정해야 하는지 함께 알려주면 더 정확한 결과를 얻습니다.",
      "patternMd": "첫 턴이라 짚을 앞선 결과가 없어요. 그래서 이 턴에는 이름을 붙이지 않았어요."
    },
    {
      "turn": 2,
      "feedbackMd": "## 개선점\n\"고쳐줘\"처럼 대상이 모호한 표현 대신 기대 동작을 서술하세요.",
      "patternMd": "### 이 턴의 이름\nvibe coding — AI가 낸 코드를 읽지 않고 받아들이는 방식\n턴 1에서 AI가 Main.java를 고쳤는데 이 턴 프롬프트에 그 이름이 안 나와요.\n\n### 쓸 기법\nhuman review — 이 턴 프롬프트를 쓰기 전에 턴 1이 바꾼 Main.java를 열어 봤다면 그게 human review예요"
    }
  ],
  "overallMd": "## 전체 평가\n요구사항을 단계적으로 좁혀간 흐름이 좋았습니다.\n\n## 다음 세션 제안\n첫 프롬프트에 입출력 예시를 포함해 보세요.",
  "patternOverallMd": "### 이번 세션의 이름\nvibe coding\n이름이 붙는 1턴에서 앞 턴이 바꾼 파일을 프롬프트가 안 짚었어요.\n\n### 다음 문제에 가져갈 것\nhuman review\n\n---\n여기 쓴 용어는 AI Coding Dictionary에서 가져왔어요. https://aicodingdictionary.com"
}
```

구버전 어템프트(턴별 피드백 없음):

```json
{
  "turns": [],
  "overallMd": "## 전체 평가\n요구사항을 단계적으로 좁혀간 흐름이 좋았습니다.",
  "patternOverallMd": null
}
```

pattern 피드백 도입 이전 어템프트(턴별 피드백은 있음):

```json
{
  "turns": [
    {
      "turn": 1,
      "feedbackMd": "## 개선점\n어떤 파일을 수정해야 하는지 함께 알려주세요.",
      "patternMd": null
    }
  ],
  "overallMd": "## 전체 평가\n요구사항을 단계적으로 좁혀간 흐름이 좋았습니다.",
  "patternOverallMd": null
}
```

### GET /api/attempts/{id}/feedback

제출 시 생성해 저장한 피드백을 두 렌즈(프롬프트·작업 방식) 모두 턴별과 전체로 반환한다. 제출 전에는 조회할 수 없다.

- **인증**: 불필요. 피드백은 제출된 어템프트에만 있고, **제출된 어템프트의 피드백은 누구나** 조회할 수 있다 ([자세히](#제출된-어템프트는-공개된다)).

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 어템프트 ID |

**성공 응답 — 200**: [FeedbackResponse](#feedbackresponse-계약)

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 404 | `attempt-not-found` | 해당 ID의 어템프트가 없음, 또는 **남의 진행 중 어템프트** |
| 404 | `feedback-not-found` | 내 어템프트가 아직 `IN_PROGRESS` — 제출 후 조회 가능 |

---

# 인증 (Auth / Me)

## MeResponse 공통 스키마

`POST /api/auth/signup`(201), `POST /api/auth/login`(200), `GET /api/me`(200)가 모두 이 형태를 반환한다. 비밀번호 해시는 어떤 응답에도 포함되지 않는다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number | 사용자 ID |
| `username` | string | 아이디 |
| `nickname` | string | 닉네임 |
| `email` | string | 이메일 |
| `createdAt` | string | 가입 시각 (ISO-8601) |

```json
{
  "id": 1,
  "username": "prompter01",
  "nickname": "프롬프터",
  "email": "prompter@example.com",
  "createdAt": "2026-07-29T05:00:00Z"
}
```

### POST /api/auth/signup

아이디·비밀번호·닉네임·이메일로 계정을 생성한다. 비밀번호는 BCrypt 해시로만 저장된다.

- **인증**: 불필요
- **주의**: 가입만으로는 로그인되지 않는다(세션 미생성). 로그인 상태가 되려면 클라이언트가 이어서 `POST /api/auth/login`을 호출해야 한다.

**요청 바디**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `username` | string | 필수 | 3~30자, 공백만 불가 | 로그인에 사용할 아이디 |
| `password` | string | 필수 | 8~100자, 공백만 불가 | 비밀번호 |
| `nickname` | string | 필수 | 2~30자, 공백만 불가 | 화면에 표시할 닉네임 |
| `email` | string | 필수 | 이메일 형식, 최대 255자 | 이메일 |

```json
{
  "username": "prompter01",
  "password": "password123!",
  "nickname": "프롬프터",
  "email": "prompter@example.com"
}
```

**성공 응답 — 201 Created**: [MeResponse](#meresponse-공통-스키마)

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 400 | `invalid-request` | 형식·길이 검증 실패 |
| 409 | `duplicate-username` | 이미 사용 중인 아이디 |
| 409 | `duplicate-email` | 이미 사용 중인 이메일 |
| 409 | `duplicate-user` | 동시 가입 레이스에서 DB UNIQUE 제약에 걸린 경우(안전망) |

### POST /api/auth/login

아이디/비밀번호를 검증하고 세션을 생성한다.

- **인증**: 불필요

**요청 바디**

| 필드 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `username` | string | 필수 | 공백만 불가 | 아이디 |
| `password` | string | 필수 | 공백만 불가 | 비밀번호 |

```json
{ "username": "prompter01", "password": "password123!" }
```

**성공 응답 — 200**: [MeResponse](#meresponse-공통-스키마)

응답 헤더에 `Set-Cookie: JSESSIONID=...; HttpOnly`가 실린다. 응답 본문이 곧 사용자 정보이므로 로그인 직후 `GET /api/me`를 다시 호출할 필요는 없다.

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 400 | `invalid-request` | `username`/`password` 누락 |
| 401 | `bad-credentials` | 아이디 없음 또는 비밀번호 불일치 (계정 열거 방지를 위해 두 경우가 동일한 응답) |

### POST /api/auth/logout

세션을 무효화한다.

- **인증**: 불필요 (멱등 — 로그인 상태가 아니거나 세션이 이미 만료됐어도 204)
- **요청 바디**: 없음

**성공 응답 — 204 No Content** (본문 없음)

**에러**: 없음

### GET /api/me

세션 쿠키로 식별된 현재 로그인 사용자의 정보를 반환한다. SPA가 앱 시작 시 로그인 여부를 확인하는 용도로도 쓴다.

- **인증**: **필요** (세션 쿠키)
- **파라미터**: 없음. 사용자 ID는 URL·바디로 받지 않고 세션에서만 얻는다.

**성공 응답 — 200**: [MeResponse](#meresponse-공통-스키마)

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 401 | `unauthenticated` | 세션이 없거나 만료됨 |
| 403 | `access-denied` | 인증됐으나 권한 부족 (현재 권한 등급이 하나라 사실상 미발생) |

### GET /api/me/attempts

현재 로그인 사용자가 **제출 완료한** 풀이 목록을 제출 시각 내림차순으로 반환한다. 같은 문제를 여러 번 제출했으면 각각 별도 항목이다. 제출 전(`IN_PROGRESS`) 어템프트는 포함되지 않는다.

- **인증**: **필요** (세션 쿠키)
- **파라미터**: 없음

**성공 응답 — 200**

봉투 없이 배열을 그대로 반환한다(다른 목록 API와 형태가 다르다).

| 필드 | 타입 | 설명 |
|---|---|---|
| `[].attemptId` | number | 어템프트 ID. 피드백 조회(`GET /api/attempts/{id}/feedback`)에 쓴다 |
| `[].problemId` | number | 문제 ID |
| `[].problemTitle` | string | 문제 제목 |
| `[].submittedAt` | string \| null | 제출 완료 시각 (ISO-8601). 제출 시각 기록 이전의 오래된 기록은 null일 수 있다 |

```json
[
  { "attemptId": 42, "problemId": 3, "problemTitle": "전화번호 개인정보 보호 처리", "submittedAt": "2026-07-31T05:10:32Z" }
]
```

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 401 | `unauthenticated` | 세션이 없거나 만료됨 |

> 게스트로 제출한 풀이도 **게스트 세션이 유효한 동안 로그인하면** 소유권이 이전되어(위 [소유권](#소유권)) 이 목록에 함께 나타난다. 이전은 제출 여부와 무관하게 그 게스트의 모든 어템프트에 적용된다. 반대로 게스트 세션이 만료된 뒤 로그인하면 이전되지 않으므로, 그 풀이는 이 목록에도 나오지 않고 어템프트 API로도 접근할 수 없다.
