# Spring Boot 4.1.0 기본 제공 관측성(Observability) 조사

> 목적: **인프라를 새로 세우지 않고**(대시보드/알림/별도 워커 없이) 배포된 EC2에서 사후에 장애를 진단할 수 있는 수단을 찾는다.
> 대상: `backend/` — Spring Boot 4.1.0, Java 21, Gradle, 단일 EC2 docker compose 배포, 로그는 stdout(`docker logs`)만.
> 모든 항목은 4.1 1차 자료(레퍼런스 문서 / `v4.1.0` 태그 소스 / Maven Central POM)로 확인했다. 확인하지 못한 것은 맨 아래 [미확인 목록](#미확인-목록)에 모았다.

---

## 요약

| 얻는 것 | 필요한 의존성 |
|---|---|
| **JSON 구조화 로그**(ECS/GELF/Logstash), 커스텀 필드 추가, MDC 자동 포함 | **없음** (코어 기능) |
| **로그 파일 출력 + 회전/압축/보존** | **없음** (코어 기능) |
| HTTP 요청 로깅 (`CommonsRequestLoggingFilter`) | **없음** (`spring-web`에 포함) |
| **traceId/spanId를 MDC에 채우는 로그 상관관계** | `spring-boot-micrometer-tracing-brave` + `micrometer-tracing-bridge-brave` **2개**. **익스포터·백엔드 불필요** |
| `/actuator/health`, `/actuator/info`, `/actuator/loggers`, `/actuator/httpexchanges` | `spring-boot-starter-actuator` **1개** |
| Spring AI LLM 호출의 토큰 수·지연·finish reason 기록, 프롬프트/응답 내용 로깅 | `spring-boot-starter-actuator` **또는** 위 tracing 2종 중 아무거나 (둘 다 `ObservationRegistry` 빈을 제공) |
| 메트릭 대시보드, 트레이스 백엔드 | Prometheus/Zipkin/OTLP — **이번 범위 밖** |

핵심 결론 두 가지:

1. **네이티브 JSON 로깅은 의존성 0개로 즉시 가능하다.** `logging.structured.format.console=ecs` 한 줄이면 끝이고, MDC가 기본으로 JSON에 들어간다.
2. **로그 상관관계(traceId)는 트레이스 백엔드 없이 가능하다.** 다만 "의존성 0개"는 **불가능**하다 — `io.micrometer.tracing.Tracer`가 클래스패스에 있어야만 상관관계 ID 기능 전체가 켜지기 때문이다. 대신 Zipkin/OTLP 익스포터는 전혀 필요 없고, 브리지 2개만 넣으면 된다.

> 📌 **기준 브랜치는 `BE-main`이다.** 이 문서를 쓸 때 작업트리가 `FE/feedback-in-progress-notice`에 체크아웃되어 있어 한때 "amqp가 없다"고 적었으나, 통합 브랜치 `BE-main`의 `backend/build.gradle`에는 `spring-boot-starter-amqp`가 **있고** `rabbit/` 패키지와 `CodeRunService`도 있다. 따라서 아래 3번의 RabbitMQ 헬스 인디케이터는 **해당된다**.
>
> `BE-main` 기준 로깅 실태: 호출지점 25곳(`log.error` 9 / `log.info` 9 / `log.warn` 7), 7개 파일(`ai/OpenAiCodeGenerator` 8, `ai/OpenAiFeedbackGenerator` 5, `attempt/service/CodeRunService` 4, `rabbit/CodeRunResultListener` 3, `gitlab/GitLabProblemSourceClient` 2, `problem/service/ProblemSyncScheduler` 2, `problem/service/ProblemSyncService` 1). controller 계층과 `GlobalExceptionHandler`는 **로그 0줄**.

---

## 1. 구조화 로깅 (JSON)

### 되는가

**추가 의존성 없이 된다.** Spring Boot 3.4에서 도입된 코어 기능이고(`LoggingSystemProperty.CONSOLE_STRUCTURED_FORMAT`에 `@since 3.4.0`), 4.1에서도 `core/spring-boot` 모듈에 그대로 있다. actuator·micrometer·logstash-logback-encoder 전부 불필요하다.

### 지원 포맷

출처: <https://docs.spring.io/spring-boot/reference/features/logging.html> (Structured Logging)

| 포맷 | format id |
|---|---|
| Elastic Common Schema | `ecs` |
| Graylog Extended Log Format | `gelf` |
| Logstash | `logstash` |
| 직접 구현 | `StructuredLogFormatter<ILoggingEvent>` 구현체의 FQN |

`StructuredLogFormatter`의 패키지는 `org.springframework.boot.logging.structured.StructuredLogFormatter`.

### 프로퍼티 (전부 4.1 프로퍼티 부록에서 확인)

출처: <https://docs.spring.io/spring-boot/appendix/application-properties/index.html>

```properties
logging.structured.format.console      # format id 또는 FQN. 기본값 없음(=비활성)
logging.structured.format.file         # 동일. console/file을 따로 지정 가능
```

JSON 조작:

| 키 | 설명 | 기본값 |
|---|---|---|
| `logging.structured.json.include` | 포함할 멤버 경로 | (없음) |
| `logging.structured.json.exclude` | 제외할 멤버 경로 | (없음) |
| `logging.structured.json.rename.*` | 멤버 경로 → 대체 이름 매핑 | (없음) |
| `logging.structured.json.add.*` | **임의 필드 추가** | (없음) |
| `logging.structured.json.customizer` | `StructuredLoggingJsonMembersCustomizer` 구현체 FQN 목록 | (없음) |
| `logging.structured.json.context.include` | **MDC/컨텍스트 데이터를 JSON에 포함** | **`true`** |
| `logging.structured.json.context.prefix` | 컨텍스트 데이터에 붙일 접두어 | (없음) |
| `logging.structured.json.stacktrace.root` | `first` \| `last` | (없음) |
| `logging.structured.json.stacktrace.max-length` | 출력 최대 길이 | (없음) |
| `logging.structured.json.stacktrace.max-throwable-depth` | throwable 최대 깊이 | (없음) |
| `logging.structured.json.stacktrace.include-common-frames` | 공통 프레임 포함 | (없음) |
| `logging.structured.json.stacktrace.include-hashes` | 스택트레이스 해시 포함 | (없음) |
| `logging.structured.json.stacktrace.printer` | `standard` \| `logging-system` \| `StackTracePrinter` FQN | (없음) |

`context.include` 기본값 `true`는 소스에서 확인: `StructuredLoggingJsonProperties.Context`가
`record Context(@DefaultValue("true") boolean include, @Nullable String prefix)` (`@since 3.5.0`)
([소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/structured/StructuredLoggingJsonProperties.java)).

서비스 메타데이터:

```properties
logging.structured.ecs.service.name         # 기본값: spring.application.name
logging.structured.ecs.service.version      # 기본값: spring.application.version
logging.structured.ecs.service.environment
logging.structured.ecs.service.node-name
logging.structured.gelf.host                # 기본값: spring.application.name
logging.structured.gelf.service.version     # 기본값: spring.application.version
```

### MDC가 JSON에 들어가는가 — **들어간다**

이게 이번 조사에서 가장 실용적인 사실이다. 두 포맷터 소스 모두 MDC 맵을 그대로 JSON 멤버로 흘려보낸다.

- ECS ([소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/ElasticCommonSchemaStructuredLogFormatter.java)):
  `members.add().usingPairs(contextPairs.nested((pairs) -> { pairs.addMapEntries(ILoggingEvent::getMDCPropertyMap); ... }))` — **중첩(nested)** 구조로 삽입.
- Logstash ([소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/LogstashStructuredLogFormatter.java)):
  `contextPairs.flat("_", (pairs) -> pairs.addMapEntries(ILoggingEvent::getMDCPropertyMap))` — **평탄화 + `_` 접두어**.

즉 `MDC.put("attemptId", ...)`만 해두면 별도 커스터마이저 없이 JSON 필드로 나온다. 2번의 traceId/spanId도 MDC를 타고 들어오므로 자동으로 JSON에 실린다.

또한 SLF4J의 fluent API `KeyValuePair`(`log.atInfo().addKeyValue("k", v).log(...)`)도 같은 자리에 들어간다(위 ECS 소스의 `pairs.add(ILoggingEvent::getKeyValuePairs, keyValuePairExtractor)`).

ECS 출력의 고정 멤버는 `@timestamp`, `log.level`, `log.logger`, `process.pid`, `process.thread.name`, `message`, `error.type` / `error.message` / `error.stack_trace`, `tags`, `ecs.version` (= `"8.11"`).

### 3.x 대비 변경점

- 포맷 자체(`ecs`/`gelf`/`logstash`)와 `logging.structured.format.*`는 **3.4에서 도입**, 4.1까지 동일. Boot 4에서 이름이 바뀐 게 없다.
- `logging.structured.json.*`(include/exclude/rename/add/customizer)와 `logging.structured.json.context.*`는 **3.5에서 추가**된 것으로, 3.4만 쓰던 사람에겐 새 기능이다.
- 4.1 릴리스 노트에 구조화 로깅 관련 변경은 없다 (<https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes>).

### 의존성

**추가 의존성 없음.** `spring-boot-starter-web`이 이미 가져오는 `spring-boot-starter-logging`(Logback)만으로 동작한다.

---

## 2. 상관관계 ID (correlation id)

### 되는가

**추가 의존성 없이는 안 된다.** Micrometer Tracing이 클래스패스에 있어야 한다.
다만 **익스포터/백엔드는 필요 없다** — 브리지만 넣으면 MDC에 traceId/spanId가 채워지고 스팬은 아무 데도 안 보낸다.

### 활성화 조건 (소스로 확인한 정확한 사슬)

1. `LogCorrelationEnvironmentPostProcessor`가 `io.micrometer.tracing.Tracer` 클래스 존재를 확인하고, 존재할 때만 프로퍼티 소스를 추가한다. 그 프로퍼티는 `logging.expect-correlation-id`이고, **값은 `management.tracing.export.enabled`(기본 `true`)를 그대로 위임**한다.
   ([소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/module/spring-boot-micrometer-tracing/src/main/java/org/springframework/boot/micrometer/tracing/autoconfigure/LogCorrelationEnvironmentPostProcessor.java))

   ```java
   if (ClassUtils.isPresent("io.micrometer.tracing.Tracer", application.getClassLoader())) {
       environment.getPropertySources().addLast(new LogCorrelationPropertySource(this, environment));
   }
   // ...
   return this.environment.getProperty("management.tracing.export.enabled", Boolean.class, Boolean.TRUE);
   ```

2. `AbstractLoggingSystem.getDefaultValueResolver()`가 `logging.expect-correlation-id`가 `true`일 때만 `logging.pattern.correlation`의 기본값을 채운다.
   ([소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/AbstractLoggingSystem.java))

3. Logback의 그 기본값은 **`%correlationId`** (`LogbackLoggingSystem.getDefaultLogCorrelationPattern()`).

4. `%correlationId` → `CorrelationIdConverter` → `CorrelationIdFormatter.DEFAULT`, 이 값은
   **`CorrelationIdFormatter.of("traceId(32),spanId(16)")`** 이고 MDC에서 값을 읽어 `[<traceId>-<spanId>] ` (대괄호 + **뒤에 공백 1칸**) 형태로 렌더링한다. 값이 하나도 없으면 같은 폭의 공백(`blank`)을 출력한다.
   ([CorrelationIdFormatter 소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/CorrelationIdFormatter.java))

정리하면 **`logging.pattern.correlation`의 "기본값"은 문서상 "로깅 시스템마다 다름"이고, Logback에서는 `%correlationId`이며 실질 출력 포맷은 `[traceId-spanId] `**다. 프로퍼티 부록에도 그대로 "Its default value varies according to the logging system"으로 적혀 있다.

문서에 나온 Sleuth 호환 커스터마이즈 예시 (<https://docs.spring.io/spring-boot/reference/actuator/tracing.html>):

```yaml
logging:
  pattern:
    correlation: "[${spring.application.name:},%X{traceId:-},%X{spanId:-}] "
  include-application-name: false
```

### ⚠️ 함정: `management.tracing.export.enabled=false`로 "익스포트만 끄기"는 하지 말 것

위 1번에서 봤듯 이 프로퍼티는 **상관관계 ID 기본 패턴의 스위치를 겸한다**. `false`로 두면 익스포트뿐 아니라 로그의 traceId 출력까지 사라진다. "백엔드 없이 상관관계만"을 원하면 이 값은 건드리지 말고 **익스포터 의존성을 아예 안 넣는 방식**을 써야 한다.

(참고: Boot 4.0에서 `management.tracing.enabled` → `management.tracing.export.enabled`로 **이름이 바뀌었다**. <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes>)

### 익스포터 없이 되는가 — **된다**

`BraveAutoConfiguration`의 조건은 `@ConditionalOnClass({ Tracer.class, BraveTracer.class })` 뿐이고, `Tracing` 빈을 만들 때 `List<SpanHandler> spanHandlers`를 주입받아 `spanHandlers.forEach(builder::addSpanHandler)` 하므로 **핸들러가 0개여도 정상 기동한다**. Zipkin 리포터 빈은 조건이 아니다.
([소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/module/spring-boot-micrometer-tracing-brave/src/main/java/org/springframework/boot/micrometer/tracing/brave/autoconfigure/BraveAutoConfiguration.java))

### 필요한 정확한 좌표

Boot 4에서 트레이싱 모듈이 **전면 재편**됐다. 3.x에서 손으로 조합하던 것이 전용 스타터로 정리됐고, Boot 자체 모듈이 브리지와 분리됐다.

Boot 4.1.0 BOM에 존재하는 관련 아티팩트(로컬 `spring-boot-dependencies-4.1.0.pom`에서 확인):
`spring-boot-micrometer-observation`, `spring-boot-micrometer-tracing`, `spring-boot-micrometer-tracing-brave`, `spring-boot-micrometer-tracing-opentelemetry`, `spring-boot-zipkin`, `spring-boot-opentelemetry`, `spring-boot-starter-zipkin`, `spring-boot-starter-opentelemetry`.

| 목적 | 좌표 |
|---|---|
| **MDC 상관관계만 (백엔드 없음) — 이번 목적에 맞는 조합** | `org.springframework.boot:spring-boot-micrometer-tracing-brave`<br>`io.micrometer:micrometer-tracing-bridge-brave` |
| Brave + Zipkin 전송 | `org.springframework.boot:spring-boot-starter-zipkin` |
| OpenTelemetry + OTLP 전송 | `org.springframework.boot:spring-boot-starter-opentelemetry` |

`spring-boot-starter-zipkin:4.1.0`의 POM은 정확히 다음 4개다 (Maven Central 확인):
`spring-boot-starter`, `spring-boot-micrometer-tracing-brave`, `spring-boot-zipkin`, `io.micrometer:micrometer-tracing-bridge-brave:1.7.0`.
→ 여기서 **`spring-boot-zipkin`만 빼면** 익스포터 없는 구성이 된다.

`spring-boot-micrometer-tracing-brave:4.1.0`의 POM은 `spring-boot`, `spring-boot-micrometer-observation`, `spring-boot-micrometer-tracing`, `io.micrometer:micrometer-tracing:1.7.0`.
→ **actuator를 요구하지 않는다.** 즉 상관관계만 원하면 actuator 없이도 된다.
→ 덤으로 `spring-boot-micrometer-observation`이 딸려오므로 **`ObservationRegistry` 빈이 생긴다** (6번 Spring AI와 연결되는 지점).

관리 버전: Micrometer `1.17.0`, Micrometer Tracing `1.7.0` (Boot 4.1.0 BOM / 로컬 Gradle 캐시 양쪽에서 확인).

### 샘플링

- `management.tracing.sampling.probability` 기본값 **`0.1`** (프로퍼티 부록에서 확인). 문서 산문에도 "By default, Spring Boot samples only 10% of requests"라고 나온다.
- OpenTelemetry를 쓸 때만 `management.opentelemetry.tracing.sampler`로 샘플러를 고를 수 있고 기본은 `parent-based-trace-id-ratio` (4.1 신규).
- **샘플링에서 탈락한 트레이스도 로그에 id가 찍히는지는 1차 자료에서 확인하지 못했다** → [미확인 목록](#미확인-목록) 참조. 어차피 익스포트를 안 하므로 `probability: 1.0`으로 두면 이 질문 자체가 사라진다(전송 비용이 0이므로 부작용도 없다).

### 의존성

`org.springframework.boot:spring-boot-micrometer-tracing-brave` + `io.micrometer:micrometer-tracing-bridge-brave` **2개**. 버전은 Boot 플러그인이 관리하므로 생략 가능. **actuator·익스포터·백엔드 불필요.**

---

## 3. Actuator

### 좌표 — 4.1에서도 그대로

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

출처: <https://docs.spring.io/spring-boot/reference/actuator/enabling.html>. 이름은 안 바뀌었지만 **내부 구성은 Boot 4에서 쪼개졌다.** `spring-boot-starter-actuator:4.1.0` POM의 직접 의존성(Maven Central 확인):

| 아티팩트 | 버전 |
|---|---|
| `org.springframework.boot:spring-boot-starter` | 4.1.0 |
| `org.springframework.boot:spring-boot-starter-micrometer-metrics` | 4.1.0 |
| `org.springframework.boot:spring-boot-actuator-autoconfigure` | 4.1.0 |
| `org.springframework.boot:spring-boot-health` | 4.1.0 |
| `io.micrometer:micrometer-observation` | 1.17.0 |
| `io.micrometer:micrometer-jakarta9` | 1.17.0 |

→ **트레이싱은 안 딸려온다.** actuator를 넣어도 traceId는 안 생긴다(2번을 별도로 해야 함). 반대로 `micrometer-observation`은 딸려오므로 `ObservationRegistry` 빈은 생긴다.

### 기본 노출

출처: <https://docs.spring.io/spring-boot/reference/actuator/endpoints.html> + 프로퍼티 부록

| 프로퍼티 | 기본값 |
|---|---|
| `management.endpoints.web.exposure.include` | **`[health]`** — HTTP로는 health 하나만 |
| `management.endpoints.web.exposure.exclude` | (비어 있음) |
| `management.endpoints.jmx.exposure.include` | `[health]` |
| `management.endpoints.web.base-path` | **`/actuator`** |
| `management.endpoint.health.access` | `unrestricted` |

`base-path`는 서블릿 컨텍스트 경로(`server.servlet.context-path`) 기준 상대 경로이며, 관리 서버가 메인 서버 포트를 공유할 때 그렇다(별도 `management.server.port`를 쓰면 그 포트 루트 기준). 예: `/actuator/health`.

Boot 4에는 접근 제어 프로퍼티도 있다: `management.endpoints.access.default` (`none` | `read-only` | `unrestricted`), `management.endpoint.<id>.access`, `management.endpoints.access.max-permitted`.

내장 엔드포인트 전체: `auditevents`, `beans`, `caches`, `conditions`, `configprops`, `env`, `flyway`, `health`, `httpexchanges`, `info`, `integrationgraph`, `loggers`, `liquibase`, `metrics`, `mappings`, `quartz`, `scheduledtasks`, `sessions`, `shutdown`, `startup`, `threaddump` + 웹 전용 `heapdump`, `logfile`, `prometheus`.

### Health

| 프로퍼티 | 값 / 기본값 |
|---|---|
| `management.endpoint.health.show-details` | `never`(기본) \| `when-authorized` \| `always` |
| `management.endpoint.health.show-components` | 미지정 시 `show-details`를 따름 |

헬스 그룹:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: "db"
          show-details: "when-authorized"
          roles: "admin"
```

→ `/actuator/health/readiness`로 접근.

### DataSource / RabbitMQ 헬스 인디케이터 자동 등록 — **된다**

Boot 4에서 헬스 인디케이터가 actuator 모듈 밖으로 **이사했다** (3.x의 `org.springframework.boot.actuate.jdbc` / `.actuate.amqp`가 아님):

| 대상 | 4.1 FQN | 소속 모듈 |
|---|---|---|
| DataSource | `org.springframework.boot.jdbc.health.DataSourceHealthIndicator` | `spring-boot-jdbc` |
| RabbitMQ | `org.springframework.boot.amqp.health.RabbitHealthIndicator` | `spring-boot-amqp` |

자동 설정 조건 (소스 확인):

```java
// DataSourceHealthContributorAutoConfiguration
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass({ JdbcTemplate.class, AbstractRoutingDataSource.class, ConditionalOnEnabledHealthIndicator.class })
@ConditionalOnBean(DataSource.class)
@ConditionalOnEnabledHealthIndicator("db")

// RabbitHealthContributorAutoConfiguration
@AutoConfiguration(after = RabbitAutoConfiguration.class)
@ConditionalOnClass({ RabbitHealthIndicator.class, RabbitTemplate.class, ConditionalOnEnabledHealthIndicator.class })
@ConditionalOnBean(RabbitTemplate.class)
@ConditionalOnEnabledHealthIndicator("rabbit")
```

- **DataSource**: 이 레포는 `spring-boot-starter-data-jpa` + postgresql 드라이버로 `DataSource` 빈이 있으므로 **자동 등록된다**. 헬스 이름은 `db`. Postgres 전용 인디케이터가 따로 있는 게 아니라 `DataSourceHealthIndicator`가 검증 쿼리를 돌린다.
- **RabbitMQ**: `RabbitTemplate` 빈이 있어야 한다. **현재 브랜치엔 amqp 의존성이 없으므로 등록되지 않는다.**

### Spring Security와의 조합

`EndpointRequest.toAnyEndpoint()`는 **여전히 관용구**지만 **패키지가 바뀌었다**:

| | FQN |
|---|---|
| 3.x | `org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest` |
| **4.1** | **`org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest`** (모듈 `spring-boot-security`) |

4.1 트리에서 확인한 실제 경로: `module/spring-boot-security/src/main/java/org/springframework/boot/security/autoconfigure/actuate/web/servlet/EndpointRequest.java` (리액티브용은 `.../actuate/web/reactive/`).

문서가 제시하는 관용구 (<https://docs.spring.io/spring-boot/reference/actuator/endpoints.html>):

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher(EndpointRequest.toAnyEndpoint());
    http.authorizeHttpRequests((requests) -> requests.anyRequest().hasRole("ENDPOINT_ADMIN"));
    http.httpBasic(withDefaults());
    return http.build();
}
```

- `EndpointRequest.toAnyEndpoint()` — 모든 엔드포인트
- `EndpointRequest.to("health")` — 특정 엔드포인트와 하위 경로(`/actuator/health/**`)

> ⚠️ **이 레포에 특히 중요**: 문서는 "커스텀 `SecurityFilterChain`이 없으면 `/health` 외 전부 자동으로 보호된다"고 하는데, `backend/src/main/java/com/promptstudio/global/config/SecurityConfig.java:74`는 `anyRequest().permitAll()`이다. 즉 **actuator를 그냥 추가하면 모든 노출 엔드포인트가 전부 공개된다.** 반드시 `@Order`가 더 높은 별도 체인을 추가해야 한다 (아래 7번에 스니펫).

### `/actuator/info`에 git commit + build info 채우기

**build info** — `springBoot { buildInfo() }`는 **4.1에서도 유효**하다 (<https://docs.spring.io/spring-boot/gradle-plugin/integrating-with-actuator.html>).

```gradle
springBoot {
    buildInfo()
}
```

→ `META-INF/build-info.properties` 생성 → `BuildProperties` 빈 → `BuildInfoContributor`.
자동 설정 조건: `@ConditionalOnEnabledInfoContributor("build")` + `@ConditionalOnSingleCandidate(BuildProperties.class)`.

**git info** — Spring이 `git.properties`를 만들어주지는 않는다. 클래스패스에 `git.properties`가 있어야 `GitProperties` 빈이 생기고(`ProjectInfoAutoConfiguration`), 그때 `GitInfoContributor`가 붙는다. 조건: `@ConditionalOnEnabledInfoContributor("git")` + `@ConditionalOnSingleCandidate(GitProperties.class)`.
`git.properties` 생성은 서드파티 Gradle 플러그인(`com.gorylenko.gradle-git-properties`)이 통상적인 방법이며, **이건 Spring 1차 자료로 확인할 수 없다** → 미확인 목록.

info 관련 프로퍼티 기본값 (프로퍼티 부록):

| 프로퍼티 | 기본값 |
|---|---|
| `management.info.git.enabled` | `true` |
| `management.info.git.mode` | **`simple`** (커밋 id/시각만. `full`이면 전체 노출) |
| `management.info.build.enabled` | `true` |
| `management.info.defaults.enabled` | `true` |
| `management.info.env.enabled` | **`false`** |
| `management.info.java.enabled` | `false` |
| `management.info.os.enabled` | `false` |

4.1 신규: info 엔드포인트에 `process.uptime`, `process.startTime`, `process.currentTime`, `process.timezone`, `process.locale`, `process.workingDirectory`가 추가됐다 (<https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes>). `management.info.process.enabled`가 필요하다(자동설정에 `@ConditionalOnEnabledInfoContributor(value = "process", fallback = InfoContributorFallback.DISABLE)` → 기본 비활성).

> ⚠️ `info`는 기본 노출 대상이 아니다. `management.endpoints.web.exposure.include`에 명시해야 보인다.

### 의존성

`org.springframework.boot:spring-boot-starter-actuator` **1개**. DataSource 헬스는 추가 의존성 없음. Rabbit 헬스는 `spring-boot-starter-amqp`가 있어야 함(현재 없음). git info는 서드파티 Gradle 플러그인 필요.

---

## 4. 로그 파일 출력 + 회전

### 되는가

**추가 의존성 없이 된다.** `logging.file.name` / `logging.file.path` / `logging.logback.rollingpolicy.*` 전부 4.1에서 유효하다.

- `logging.file.name` — 파일명(상대/절대 경로 가능)
- `logging.file.path` — 디렉터리. 그 안에 `spring.log`가 생긴다
- 둘 다 지정하면 `logging.file.name`만 쓰인다

### 회전 정책 기본값 — 소스와 부록 양쪽에서 교차 확인

`DefaultLogbackConfiguration.setRollingPolicy()`가 `SizeAndTimeBasedRollingPolicy`를 쓰며 기본값을 리터럴로 갖고 있다
([소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/DefaultLogbackConfiguration.java)):

```java
rollingPolicy.setFileNamePattern(resolve(config, "${LOGBACK_ROLLINGPOLICY_FILE_NAME_PATTERN:-${LOG_FILE}.%d{yyyy-MM-dd}.%i.gz}"));
rollingPolicy.setCleanHistoryOnStart(resolveBoolean(config, "${LOGBACK_ROLLINGPOLICY_CLEAN_HISTORY_ON_START:-false}"));
rollingPolicy.setMaxFileSize(resolveFileSize(config, "${LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE:-10MB}"));
rollingPolicy.setTotalSizeCap(resolveFileSize(config, "${LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP:-0}"));
rollingPolicy.setMaxHistory(resolveInt(config, "${LOGBACK_ROLLINGPOLICY_MAX_HISTORY:-7}"));
```

| 프로퍼티 | 기본값 | 비고 |
|---|---|---|
| `logging.logback.rollingpolicy.file-name-pattern` | `${LOG_FILE}.%d{yyyy-MM-dd}.%i.gz` | **`.gz` → 회전된 파일은 기본적으로 gzip 압축된다** |
| `logging.logback.rollingpolicy.max-file-size` | **`10MB`** | 이 크기를 넘으면 회전 |
| `logging.logback.rollingpolicy.max-history` | **`7`** | 보관 아카이브 개수(패턴이 일 단위이므로 사실상 7일) |
| `logging.logback.rollingpolicy.total-size-cap` | **`0B`** | **0 = 무제한.** 디스크 보호가 필요하면 반드시 명시 |
| `logging.logback.rollingpolicy.clean-history-on-start` | **`false`** | |

관련: `logging.threshold.file` 기본 `TRACE`, `logging.pattern.dateformat` 기본 `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`, `logging.pattern.level` 기본 `%5p`.

환경변수로도 같은 값을 줄 수 있다(`LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE` 등) — docker compose에서 유용하다
([`RollingPolicySystemProperty` 소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/RollingPolicySystemProperty.java)).

### 4.1 변경점

Log4j2 쪽에 회전 전략이 새로 생겼다: `logging.log4j2.rollingpolicy.strategy` = `size`(기본) | `time` | `size-and-time` | `cron`, 그리고 `.cron`, `.time-interval`(기본 `1`), `.time-modulate`(기본 `false`).
**이 레포는 Logback(기본)을 쓰므로 해당 없음.**

### 의존성

**추가 의존성 없음.**

---

## 5. 웹 요청 로깅

### (a) `CommonsRequestLoggingFilter` — 여전히 존재, 의존성 0개

출처: <https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/CommonsRequestLoggingFilter.html> (Spring Framework 7.0.8 — Boot 4.1이 쓰는 버전)

- FQN: `org.springframework.web.filter.CommonsRequestLoggingFilter` (`spring-web`에 포함 → **추가 의존성 없음**)
- 상위 클래스: `org.springframework.web.filter.AbstractRequestLoggingFilter`
- **DEBUG 레벨**로 기록한다 → 해당 로거의 레벨을 DEBUG로 내려야 보인다
- 설정: `setIncludeQueryString`, `setIncludePayload`, `setIncludeHeaders`, `setIncludeClientInfo`, `setMaxPayloadLength`, `setBeforeMessagePrefix/Suffix`, `setAfterMessagePrefix/Suffix`

### (b) Actuator `httpexchanges` 엔드포인트 — 존재하지만 리포지토리 빈을 직접 등록해야 한다

출처: <https://docs.spring.io/spring-boot/reference/actuator/http-exchanges.html>

- 엔드포인트 id: `httpexchanges`
- 필요한 빈: `org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository`
- 기본 구현: `org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository` — **마지막 100건** 보관
- **자동 설정되지 않는다.** 4.1에는 `HttpExchangesAutoConfiguration` 같은 게 없고, 엔드포인트 자동설정만 있으며 그마저도 리포지토리 빈이 있을 때만 동작한다
  ([소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/module/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure/web/exchanges/HttpExchangesEndpointAutoConfiguration.java)):

  ```java
  @AutoConfiguration
  @ConditionalOnAvailableEndpoint(HttpExchangesEndpoint.class)
  public final class HttpExchangesEndpointAutoConfiguration {
      @Bean
      @ConditionalOnBean(HttpExchangeRepository.class)
      @ConditionalOnMissingBean
      HttpExchangesEndpoint httpExchangesEndpoint(HttpExchangeRepository exchangeRepository) { ... }
  }
  ```

  → `@Bean InMemoryHttpExchangeRepository httpExchangeRepository()` 를 직접 선언해야 한다.

프로퍼티:

| 프로퍼티 | 기본값 |
|---|---|
| `management.httpexchanges.recording.enabled` | `true` |
| `management.httpexchanges.recording.include` | **`[time-taken, request-headers, response-headers]`** |

### **본문(body)은 절대 캡처되지 않는다**

`Include` enum에 body 관련 상수 자체가 없다
([소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/module/spring-boot-actuator/src/main/java/org/springframework/boot/actuate/web/exchanges/Include.java)):

`REQUEST_HEADERS`, `REMOTE_ADDRESS`, `COOKIE_HEADERS`, `AUTHORIZATION_HEADER`, `RESPONSE_HEADERS`, `PRINCIPAL`, `SESSION_ID`, `TIME_TAKEN`.

기본 포함값도 소스에서 확인: `TIME_TAKEN`, `REQUEST_HEADERS`, `RESPONSE_HEADERS` (Authorization/Cookie/Set-Cookie는 기본 제외).

한계:
- 인메모리 100건 → 재시작하면 소실, 트래픽이 조금만 있어도 밀려난다
- 문서가 명시적으로 **"개발 환경 전용"**이라고 못박고 있다 ("limited and recommended only for development environments"). 운영에는 Zipkin/OpenTelemetry를 권한다.
- 본문이 없으므로 "LLM 호출 실패 시 어떤 페이로드였나"는 알 수 없다

### 4.x 신규 사항

요청 로깅 쪽으로 4.0/4.1 릴리스 노트에 새로 추가된 기능은 **찾지 못했다**. 4.1의 웹 관련 신규는 `spring.http.clients.cookie-handling`, `InetAddressFilter`(SSRF 완화) 등 아웃바운드 HTTP 클라이언트 쪽이다.

### 의존성

- `CommonsRequestLoggingFilter`: **추가 의존성 없음**
- `httpexchanges`: `spring-boot-starter-actuator` + `HttpExchangeRepository` 빈 직접 등록

---

## 6. Spring AI 2.0.0 관측성

출처: <https://docs.spring.io/spring-ai/reference/observability/index.html> (문서 자체가 2.0.0 기준)

### 기본 계측되는가 — 계측 코드는 항상 들어 있고, 기록 여부는 `ObservationRegistry` 빈 유무에 달렸다

Spring AI는 ChatClient / ChatModel / Advisor / ToolCall / EmbeddingModel / ImageModel / VectorStore에 Micrometer Observation을 기본으로 심어둔다.

| 관측 이름 | 대상 |
|---|---|
| `spring.ai.chat.client` | `ChatClient.call()` / `.stream()` |
| **`gen_ai.client.operation`** | `ChatModel.call()` / `.stream()` |
| `spring.ai.advisor` | Advisor 실행 |
| `spring.ai.tool` | 툴 호출 |

메트릭: **`gen_ai.client.token.usage`** (카운터, `gen_ai_token_type` = `input`/`output`/`total`).

### 무엇이 캡처되는가

**Low cardinality (메트릭 + 트레이스)**: `gen_ai.operation.name`, `gen_ai.system`, `gen_ai.request.model`, `gen_ai.response.model`.

**High cardinality (트레이스에만)**: `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, `gen_ai.usage.total_tokens`, `gen_ai.usage.cache_read.input_tokens`, `gen_ai.usage.cache_creation.input_tokens`, `gen_ai.response.finish_reasons`, `gen_ai.response.id`, `gen_ai.request.temperature`, `.max_tokens`, `.top_p`, `.top_k`, `.frequency_penalty`, `.presence_penalty`, `.stop_sequences`, `.stream`, `spring.ai.model.request.tool.names`.

지연 시간은 관측 자체가 측정한다("measure the time spent on method completion").

> ⚠️ **토큰 수는 high cardinality라 "트레이스에만" 붙는다.** 트레이스 백엔드가 없으면 이 값들은 어디에도 남지 않는다. 토큰을 숫자로 남기려면 `gen_ai.client.token.usage` **메트릭**(`MeterRegistry` 필요) 쪽을 봐야 한다. 이 레포는 이미 `AttemptLlmCall`/`LlmUsageTracker`로 토큰을 DB에 적재하고 있으므로 여기서 얻을 게 크지 않다.

### 프롬프트/응답 내용 — 기본 미캡처, 명시적 opt-in

문서 원문: *"The chat prompt and completion data is typically big and possibly containing sensitive information. For those reasons, it is not exported by default."*

| 프로퍼티 | 설명 | 기본값 |
|---|---|---|
| `spring.ai.chat.observations.log-prompt` | ChatModel 프롬프트 내용 로깅 | `false` |
| `spring.ai.chat.observations.log-completion` | ChatModel 응답 내용 로깅 | `false` |
| `spring.ai.chat.observations.include-error-logging` | 관측에 에러 로깅 포함 | `false` |
| `spring.ai.chat.client.observations.log-prompt` | ChatClient 프롬프트 내용 로깅 | `false` |
| `spring.ai.chat.client.observations.log-completion` | ChatClient 응답 내용 로깅 | `false` |

(그 외 `spring.ai.image.observations.log-prompt`, `spring.ai.tools.observations.include-content`, `spring.ai.vectorstore.observations.log-query-response`도 있다.)

**이름 그대로 "log"다 — 스팬 속성이 아니라 로그로 나간다.** `ChatObservationAutoConfiguration`이 조건부로 핸들러 빈을 등록하며, 실제 출력은 `ChatModelPromptContentObservationHandler`가 `logger.info("Chat Model Prompt Content:\n" + ...)` 로 찍는다
([핸들러 소스](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-model/src/main/java/org/springframework/ai/chat/observation/ChatModelPromptContentObservationHandler.java)) —
로거 이름은 클래스 FQN `org.springframework.ai.chat.observation.ChatModelPromptContentObservationHandler`, **레벨은 INFO**.

트레이싱 유무에 따라 등록되는 핸들러가 갈린다
([자동설정 소스](https://github.com/spring-projects/spring-ai/blob/v2.0.0/auto-configurations/models/chat/observation/spring-ai-autoconfigure-model-chat-observation/src/main/java/org/springframework/ai/model/chat/observation/autoconfigure/ChatObservationAutoConfiguration.java)):

- `Tracer` 클래스 **및 빈**이 있으면 → `TracingAwareLoggingObservationHandler<>(new ChatModelPromptContentObservationHandler(), tracer)` — 로그에 트레이스 정보가 함께 실린다
- `io.micrometer.tracing.Tracer` 클래스가 **아예 없으면** → 맨 핸들러가 등록된다 (`@ConditionalOnMissingClass("io.micrometer.tracing.Tracer")`)

→ 즉 **트레이싱 없이도 프롬프트/응답 로깅은 동작한다.**

또한 자동설정은 켤 때 경고 로그를 남긴다:
> `"You have enabled logging out the prompt content with the risk of exposing sensitive or private information. Please, be careful!"`

문서의 경고도 동일: *"If you enable logging of the chat prompt and completion data, there's a risk of exposing sensitive or private information. Please, be careful!"*

### 실제로 기록되려면 필요한 의존성

문서는 "Spring Boot Metrics / Tracing 문서를 참고하라"고만 하고 좌표를 안 준다. 소스와 POM으로 확인한 실제 사정:

1. `io.micrometer:micrometer-observation`은 **이미 이 레포 클래스패스에 있다.**
   `spring-ai-starter-model-openai` → `spring-ai-openai` → `io.micrometer:micrometer-core` (compile) → `micrometer-observation` (compile). Maven Central POM으로 확인.
2. 그런데 **`ObservationRegistry` 빈은 없다.** 그 빈을 만드는 `ObservationAutoConfiguration`은 `org.springframework.boot:spring-boot-micrometer-observation` 모듈에 있고(`org.springframework.boot.micrometer.observation.autoconfigure` 패키지), 이 모듈은 현재 클래스패스에 없다.
3. 빈이 없으면 Spring AI는 **NOOP으로 떨어진다.** `OpenAiChatAutoConfiguration`이 `observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP)` 를 쓴다
   ([소스](https://github.com/spring-projects/spring-ai/blob/v2.0.0/auto-configurations/models/spring-ai-autoconfigure-model-openai/src/main/java/org/springframework/ai/model/openai/autoconfigure/OpenAiChatAutoConfiguration.java)).

따라서 **`spring-boot-micrometer-observation`을 클래스패스에 올리는 것이 필수 조건**이며, 다음 중 아무거나 하면 자동으로 딸려온다:

- `spring-boot-starter-actuator` (→ `spring-boot-starter-micrometer-metrics` → …), 또는
- `spring-boot-micrometer-tracing-brave` (2번에서 이미 추가하는 것), 또는
- `org.springframework.boot:spring-boot-micrometer-observation` 직접 추가

`gen_ai.client.token.usage` **메트릭**은 추가로 `MeterRegistry` 빈이 필요하다 (`ChatModelMeterObservationHandler`가 `@ConditionalOnBean(MeterRegistry.class)`). actuator를 넣으면 `SimpleMeterRegistry`가 붙는다.

### 의존성

- 프롬프트/응답 **로깅**만: `spring-boot-micrometer-observation` (또는 그걸 끌고 오는 actuator / tracing-brave) — 프로퍼티 opt-in 필요
- 토큰 수를 **메트릭**으로: 위 + `MeterRegistry` (actuator가 제공)
- 토큰 수를 **트레이스 속성**으로: 트레이스 백엔드 필요 → **범위 밖**

---

## 이 레포에 적용할 때의 구체적 후보

세 단계로 나눴다. 1단계만으로도 "사후 진단"이라는 목적의 대부분을 채운다.

### 1단계 — 의존성 0개: JSON 로그 + 파일 회전 + 요청 로깅

`build.gradle` 변경 **없음**. `backend/src/main/resources/application.yml`에 추가:

```yaml
spring:
  application:
    name: backend          # 이미 있음 → ECS service.name으로 자동 사용됨
    version: ${APP_VERSION:dev}   # ECS service.version으로 자동 사용됨

logging:
  structured:
    format:
      console: ecs         # docker logs가 JSON을 받게 된다
    ecs:
      service:
        environment: ${APP_ENV:local}
    json:
      add:
        deploy.host: ${HOSTNAME:unknown}
      # context.include는 기본 true → MDC가 자동으로 JSON에 실린다
      stacktrace:
        max-length: 8192
        include-common-frames: false
```

디스크에도 남기고 싶다면(도커 볼륨 마운트 전제):

```yaml
logging:
  file:
    name: /var/log/app/backend.log
  structured:
    format:
      file: ecs
  logback:
    rollingpolicy:
      max-file-size: 50MB     # 기본 10MB
      max-history: 14         # 기본 7
      total-size-cap: 2GB     # 기본 0B(무제한) → EC2 디스크 보호를 위해 반드시 지정
```

> ⚠️ 컨테이너 내부 경로에 쓰면 컨테이너 재생성 시 사라진다. `docker-compose.yml`에 볼륨을 붙이거나, 아니면 콘솔 JSON만 쓰고 도커 로그 드라이버(`max-size`/`max-file`) 쪽에서 회전을 맡기는 편이 단순하다.

요청 로깅이 필요하면 (28개 `LoggerFactory` 호출부는 그대로 두고 필터만 추가):

```java
@Bean
CommonsRequestLoggingFilter requestLoggingFilter() {
    CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
    filter.setIncludeQueryString(true);
    filter.setIncludeClientInfo(true);
    filter.setIncludeHeaders(false);   // Authorization/Cookie 유출 방지
    filter.setIncludePayload(false);   // 프롬프트 본문이 통째로 로그에 남는 것 방지
    return filter;
}
```

```yaml
logging:
  level:
    org.springframework.web.filter.CommonsRequestLoggingFilter: DEBUG
```

**직접 MDC 넣기** — 이 레포의 도메인(어템프트/제출)에서 가장 효과가 큰 건 사실 이쪽이다. 필터에서 `MDC.put("attemptId", ...)` 하면 1단계 설정만으로 JSON 필드가 된다. 별도 의존성·코드 생성 불필요.

### 2단계 — 의존성 2개: 요청 단위 상관관계 (트레이스 백엔드 없음)

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-micrometer-tracing-brave'
    implementation 'io.micrometer:micrometer-tracing-bridge-brave'
    // spring-boot-zipkin / spring-boot-starter-zipkin 은 넣지 않는다 = 스팬을 아무 데도 안 보냄
}
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0     # 익스포터가 없으므로 전량 샘플링해도 비용 0
    # export.enabled 는 절대 false로 두지 말 것 — 상관관계 패턴까지 꺼진다
```

얻는 것:

- 평문 콘솔 패턴이면 `[<traceId>-<spanId>] ` 가 로거 이름 앞에 붙는다
- 1단계의 ECS JSON을 쓰고 있다면 **`traceId`/`spanId`가 MDC를 통해 JSON 필드로 들어간다** — `docker logs | jq 'select(.traceId=="...")'` 로 요청 하나의 전체 로그를 뽑을 수 있다. 이게 이번 목적("사후 진단")의 핵심.
- 자동 설정된 `RestTemplateBuilder` / `RestClient.Builder` / `WebClient.Builder`로 만든 클라이언트는 trace를 전파한다 (직접 `new` 하면 전파 안 됨 — 문서 경고)
- **부수 효과**: `spring-boot-micrometer-observation`이 딸려와 `ObservationRegistry` 빈이 생기고, Spring AI 관측이 NOOP에서 벗어난다 (6번 참조)

### 3단계 — 의존성 1개: actuator (헬스 + 버전 확인)

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}

springBoot {
    buildInfo()      // META-INF/build-info.properties → /actuator/info
}
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,loggers   # 기본은 health 뿐
  endpoint:
    health:
      show-details: when-authorized
      group:
        readiness:
          include: db
  info:
    git:
      mode: simple    # 기본값. full로 올리면 브랜치/커밋 메시지까지 노출
```

- `db` 헬스는 `DataSource` 빈이 있으므로 **자동 등록**된다
- `loggers` 엔드포인트는 재배포 없이 런타임에 로그 레벨을 올릴 수 있어 사후 진단에 유용하다 (`POST /actuator/loggers/com.promptstudio` `{"configuredLevel":"DEBUG"}`) — 다만 쓰기 엔드포인트이므로 반드시 잠글 것

**보안 — 이 레포에선 필수.** 현재 `SecurityConfig`가 `anyRequest().permitAll()`이라 actuator를 그냥 추가하면 전부 공개된다. 우선순위가 앞선 체인을 별도로 추가한다:

```java
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest; // 4.1 패키지 주의

@Bean
@Order(1)   // 기존 filterChain보다 먼저 매칭되어야 한다
SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher(EndpointRequest.toAnyEndpoint())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(EndpointRequest.to("health")).permitAll()
            .anyRequest().hasRole("ENDPOINT_ADMIN"))
        .httpBasic(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable());
    return http.build();
}
```

기존 `filterChain` 빈에는 `@Order(2)`(또는 그보다 큰 값)를 붙여야 한다.

> 더 단순한 대안: 컨테이너 밖으로 `/actuator`를 노출하지 않는 것. nginx/보안그룹에서 `/actuator`를 막고 EC2 내부에서 `curl localhost:9090/actuator/health`로만 접근하면 위 체인 없이도 안전하다. 단일 EC2 배포라 이 편이 현실적일 수 있다.

### Spring AI 프롬프트 로깅 (2단계 또는 3단계 이후, 선택)

```yaml
spring:
  ai:
    chat:
      observations:
        log-prompt: false        # 장애 재현이 필요한 순간에만 true
        log-completion: false
        include-error-logging: true   # 에러만 남기는 건 상대적으로 안전
```

> ⚠️ `log-prompt: true`는 사용자가 입력한 프롬프트 전문이 로그에 INFO로 남는다는 뜻이다. 이 레포는 사용자 프롬프트가 곧 제품의 입력이라 내용 민감도가 높고, 이미 `AttemptLlmCall`로 DB에 필요한 정보를 적재하고 있다. 상시 켜는 것은 권하지 않는다.
> 끄고 켜는 데 재배포가 필요 없게 하려면 3단계의 `loggers` 엔드포인트가 아니라 이 프로퍼티 자체를 환경변수로 빼야 한다(프로퍼티는 런타임 변경 불가).

### 권장

**1단계 + 2단계**를 먼저 한다. 의존성 2개로 "JSON 로그 + 요청별 traceId"가 확보되고, 이건 `docker logs`만 있는 현재 상황 대비 진단 능력이 가장 크게 오르는 지점이다. actuator(3단계)는 헬스체크가 실제로 필요해질 때(로드밸런서 도입, 컨테이너 헬스체크) 넣으면 된다.

---

## 미확인 목록

1. **샘플링 탈락 트레이스의 로그 id** — `management.tracing.sampling.probability`가 1.0 미만일 때, 샘플링되지 않은 요청에도 MDC에 traceId/spanId가 채워지는지 1차 자료에서 명시적 서술을 찾지 못했다. 문서는 "Correlation IDs rely on context propagation"이라고만 한다. → **미확인 (unverified)**. 실무적으로는 `probability: 1.0`으로 우회 가능.
2. **`management.tracing.export.enabled=false`일 때 Brave가 MDC를 계속 채우는지** — 이 프로퍼티가 `logging.expect-correlation-id`를 통해 **상관관계 패턴 기본값**을 끈다는 것까지는 소스로 확인했으나, MDC 자체의 채움 여부는 확인하지 못했다. → **미확인 (unverified)**.
3. **`git.properties` 생성 방법** — `com.gorylenko.gradle-git-properties` 플러그인이 통상적인 수단이지만 Spring 1차 자료가 아니고, Gradle 9 / Boot 4.1과 호환되는 버전을 확인하지 못했다. → **미확인 (unverified)**.
4. **`CommonsRequestLoggingFilter`의 payload 캡처 시점 semantics** — Javadoc으로 API 표면(`setIncludePayload`, `setMaxPayloadLength`)과 DEBUG 레벨까지는 확인했으나, payload가 "before" 메시지에 포함되는지 "after"에만 포함되는지, 그리고 요청 본문을 이미 소비한 뒤에도 읽히는지는 확인하지 못했다. → **미확인 (unverified)**.
5. **`Tracer` 클래스는 있으나 `Tracer` 빈이 없는 경우의 Spring AI 내용 로깅** — `@ConditionalOnClass`/`@ConditionalOnBean`/`@ConditionalOnMissingClass` 조합상 두 설정 어디에도 걸리지 않아 핸들러가 등록되지 않을 것으로 보이나, 문서에 서술이 없다. → **미확인 (unverified)** (2단계를 적용하면 `Tracer` 빈이 생기므로 해당 없음).
6. **Boot 4.0 마이그레이션 가이드의 actuator 패키지 이동 전체 목록** — 위키의 마이그레이션 가이드 페이지에서 actuator 관련 old→new 패키지 매핑 전문을 찾지 못했다. 이 문서의 패키지 이동 사실(`EndpointRequest`, `DataSourceHealthIndicator`, `RabbitHealthIndicator`)은 **4.1.0 소스 트리의 실제 파일 경로**로 직접 확인한 것이다. 그 외 클래스의 이동 여부는 개별 확인이 필요하다. → **부분 미확인**.
7. **4.x 웹 요청 로깅 신규 기능** — 4.0/4.1 릴리스 노트에서 관련 항목을 찾지 못했다. "없다"는 것을 적극적으로 확인한 것은 아니다. → **미확인 (unverified)**.

---

## 참고 링크

- Logging (구조화 로깅 / 파일 / 회전): <https://docs.spring.io/spring-boot/reference/features/logging.html>
- Actuator Endpoints (노출 / 보안 / health): <https://docs.spring.io/spring-boot/reference/actuator/endpoints.html>
- Actuator Enabling (좌표): <https://docs.spring.io/spring-boot/reference/actuator/enabling.html>
- Tracing (상관관계 / 스타터 / 샘플링): <https://docs.spring.io/spring-boot/reference/actuator/tracing.html>
- Observability (`ObservationRegistry` / 컨텍스트 전파): <https://docs.spring.io/spring-boot/reference/actuator/observability.html>
- Recording HTTP Exchanges: <https://docs.spring.io/spring-boot/reference/actuator/http-exchanges.html>
- Common Application Properties (기본값 전거): <https://docs.spring.io/spring-boot/appendix/application-properties/index.html>
- Gradle Plugin — Integrating with Actuator (`buildInfo()`): <https://docs.spring.io/spring-boot/gradle-plugin/integrating-with-actuator.html>
- Spring Boot 4.0 Release Notes: <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes>
- Spring Boot 4.1 Release Notes: <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes>
- Spring AI Observability (2.0.0): <https://docs.spring.io/spring-ai/reference/observability/index.html>
- 소스 태그: <https://github.com/spring-projects/spring-boot/tree/v4.1.0>, <https://github.com/spring-projects/spring-ai/tree/v2.0.0>
