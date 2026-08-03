# Spring Boot 4.1.0 구조화 로그 — 이벤트별 필드를 JSON 1급 멤버로 올리기

> 목적: 액세스 로그 한 줄이 지금은
> `Request | method=POST route=/api/attempts/{id}/submit path=... status=502 durationMs=41230`
> 처럼 **`message` 문자열 안에 묻혀** 있다. 이걸 JSON의 **최상위 멤버**로 올려서 SQL/`jq`로 필터·집계할 수 있게 만드는 방법을 찾는다. 단, **콘솔은 사람이 읽는 평문 그대로** 둔다.
> 대상: `backend/` — Spring Boot 4.1.0, Logback, `logging.structured.format.file=ecs`.
>
> **이 문서의 모든 판정은 실행으로 검증했다.** 로컬 Gradle 캐시의 진짜 `spring-boot-4.1.0.jar`에 대해 실제 `StructuredLogEncoder`를 돌려 나온 출력이 아래 JSON들이다. 소스는 같은 좌표의 `spring-boot-4.1.0-sources.jar`(= `v4.1.0` 태그)에서 읽었다. 확인하지 못한 것은 맨 아래 [미확인 목록](#미확인-목록)에 모았다.

---

## 권장안

**SLF4J 2.x fluent API의 `addKeyValue()`를 쓴다.** 별도 클래스도, 커스터마이저도, 프로퍼티도 필요 없다. Boot 4.1의 ECS 포매터는 `ILoggingEvent.getKeyValuePairs()`를 **그대로 JSON 최상위 멤버로 출력**하며, **값의 자바 타입을 보존**한다(`status`가 `"502"`가 아니라 `502`로 나간다 — SQL에서 `status >= 500` 같은 수치 비교가 캐스팅 없이 된다). MDC와 달리 `try/finally` 정리가 필요 없고 호출 한 곳에만 스코프가 갇힌다.

```java
log.atInfo()
    .addKeyValue("method", method)          // String → JSON string
    .addKeyValue("route", route)            // "/api/attempts/{id}/submit"
    .addKeyValue("path", path)
    .addKeyValue("status", status)          // int → JSON number
    .addKeyValue("durationMs", durationMs)  // long → JSON number
    .log("Request");
```

```yaml
logging:
  file:
    name: /var/log/app/app.log      # 이게 있어야 파일 appender 자체가 생긴다
  structured:
    format:
      file: ecs                     # 파일만 JSON. console은 미설정 → 평문 유지
```

결과 (실제 출력, 들여쓰기만 추가):

```json
{"@timestamp":"...","log":{"level":"INFO","logger":"com.promptstudio.web.AccessLog"},
 "process":{"pid":12345,"thread":{"name":"http-nio-8080-exec-3"}},
 "service":{"name":"ssafy-be"},"message":"Request",
 "method":"POST","route":"/api/attempts/{id}/submit","status":502,"durationMs":41230,
 "ecs":{"version":"8.11"}}
```

`message`는 `"Request"`로 짧게 두고 변수는 전부 key-value로 넘기면 된다. 문자열 조립을 없애는 쪽이 오히려 코드가 짧아진다.

> 🔻 **콘솔 패턴도 같이 고쳐야 한다.** `%msg`는 `"Request"`만 찍으므로, 지금처럼 문자열에 필드를 박아 넣던 걸 key-value로 옮기면 **콘솔에서 필드가 통째로 사라진다.** `%kvp`를 붙여야 한다 — 자세한 건 [6. 콘솔 평문 쪽](#6-콘솔-평문-쪽--kvp).

> ⚠️ **키 이름 하나만 조심하면 된다.** ECS가 이미 쓰는 이름(`@timestamp`, `log`, `process`, `service`, `message`, `ecs`, 그리고 조건부로 `error`·`tags`)과 충돌하면 **인코딩 시점에 예외가 나고, 그 스레드의 다음 로그 한 줄이 깨진 JSON으로 나간다.** `method`/`route`/`path`/`status`/`durationMs`는 전부 안전하다 — 다만 **`error`는 예외를 함께 로깅할 때만 터지므로 개발 중에 안 잡힌다.** 확실히 막으려면 `logging.structured.json.context.prefix: app`을 걸면 되고(경로가 `app.status`로 한 단계 깊어진다), 자세한 건 [키 이름 충돌](#키-이름-충돌-실제-위험) 절 참고.

---

## 검증 방법

로컬 Gradle 캐시에 4.1.0 정품 jar가 있어서, 문서·소스 독해에 그치지 않고 **실제로 돌렸다**.

```
spring-boot-4.1.0.jar / spring-boot-4.1.0-sources.jar
  + spring-core 7.0.8, spring-context 7.0.8, spring-beans 7.0.8
  + logback-classic 1.5.34, logback-core 1.5.34, slf4j-api 2.0.18
  + jspecify 1.0.0, commons-logging 1.3.6
```

`org.springframework.boot.logging.logback.StructuredLogEncoder`(public)에 `setFormat("ecs")`를 주고, `LoggerContext`에 `Environment`를 꽂은 뒤, MDC·KeyValuePair·예외를 채운 `LoggingEvent`를 `encode()`에 넣어 나온 바이트를 그대로 읽었다. Boot가 런타임에 쓰는 경로와 동일하다(`DefaultLogbackConfiguration`이 만드는 것도 이 인코더다).

**패키지는 Boot 4에서 옮겨지지 않았다.** 구조화 로깅은 여전히 코어 `spring-boot` 모듈에 있다.

| 클래스 | 패키지 |
|---|---|
| `StructuredLogFormatter` | `org.springframework.boot.logging.structured` |
| `StructuredLoggingJsonMembersCustomizer` | `org.springframework.boot.logging.structured` |
| `ContextPairs` | `org.springframework.boot.logging.structured` |
| `ElasticCommonSchemaStructuredLogFormatter` (package-private) | `org.springframework.boot.logging.logback` |
| `StructuredLogEncoder` (public) | `org.springframework.boot.logging.logback` |

소스 트리 경로는 `core/spring-boot/src/main/java/...`다:
<https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/ElasticCommonSchemaStructuredLogFormatter.java>

---

## 1. SLF4J fluent API `KeyValuePair` — **된다**

### 전거

ECS 포매터 소스에 그대로 있다. `jsonMembers(...)` 안:

```java
members.add().usingPairs(contextPairs.nested((pairs) -> {
    pairs.addMapEntries(ILoggingEvent::getMDCPropertyMap);
    pairs.add(ILoggingEvent::getKeyValuePairs, keyValuePairExtractor);   // ← 이 줄
}));
```

<https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/ElasticCommonSchemaStructuredLogFormatter.java>

추출기는 같은 파일 상단에서 키/값을 그대로 꺼낸다:

```java
private static final PairExtractor<KeyValuePair> keyValuePairExtractor =
        PairExtractor.of((pair) -> pair.key, (pair) -> pair.value);
```

레퍼런스 문서도 ECS·GELF·Logstash 세 절에서 동일 문장으로 명시한다:

> "This format also adds every key value pair contained in the MDC to the JSON object. You can also use the SLF4J fluent logging API to add key value pairs to the logged JSON object with the `addKeyValue` method."

<https://docs.spring.io/spring-boot/4.1/reference/features/logging.html>

### 어디에 붙는가

**최상위(top level)다.** `members.add()`는 **이름 없는 멤버**이고, `JsonWriter.Member#usingPairs`의 Javadoc이 이렇게 못박는다:

> "When used with an unnamed member the result will be added to the existing JSON object"

ECS 스펙이 커스텀 필드를 `labels.*`에 두라고 하는 것과 달리, **Boot는 `labels`로 감싸지 않는다.** 실행 결과로 확인했다.

### 타입 보존 (MDC 대비 결정적 장점)

`KeyValuePair.value`는 `Object`라서 자바 타입이 그대로 JSON 타입이 된다. 실제 출력:

```json
"status":502, "durationMs":41230, "ok":true, "ratio":0.75, "maybe":null
```

MDC는 `Map<String,String>`이라 **무조건 문자열**이다(`"userId":"42"`). SQL에서 `status`를 수치로 다루려면 fluent API 쪽이 맞다.

### 점이 든 키는 중첩된다

`contextPairs.nested(...)`가 `.`을 객체 경계로 해석한다. 실행 확인:

| `addKeyValue` 키 | JSON |
|---|---|
| `route` | `"route":"/x"` |
| `http.route` | `"http":{"route":"/x"}` |
| `a.b.c` | `"a":{"b":{"c":1}}` |

ECS 표준 필드명에 맞추고 싶으면 `http.request.method` 처럼 쓰면 되지만, **기존 최상위 이름과 겹치면 터진다**(아래).

---

## 2. MDC — **된다** (단, 최상위이고 값은 전부 문자열)

같은 블록 바로 윗줄 `pairs.addMapEntries(ILoggingEvent::getMDCPropertyMap)`이다. 즉 **MDC와 KeyValuePair는 완전히 같은 자리에 같은 규칙으로 병합된다.**

### 어디에 붙는가 — `labels.*` 아니다

레퍼런스가 아니라 소스와 실행으로 확인했다. **ECS 출력에서도 MDC는 최상위**다:

```json
"message":"Request","userId":"42","traceId":"8f2c1a",...
```

기본값 `include=true`는 문서가 아니라 코드에서 나온다 — `StructuredLogFormatterFactory#getContextPairs`가 프로퍼티 미설정 시 `new Context(true, null)`을 쓴다.
<https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/structured/StructuredLogFormatterFactory.java>

### `logging.structured.json.context.prefix`의 효과

**MDC와 KeyValuePair 양쪽 모두**를 접두어 객체 아래로 밀어넣는다. `prefix: app`으로 실행한 결과:

```json
"message":"Request",
"app":{"userId":"42","traceId":"8f2c1a","method":"POST","route":"/api/attempts/{id}/submit","status":502,"durationMs":41230},
"error":{...}
```

ECS는 `nested()` 모드라 접두어가 `.`로 이어져 **객체가 된다**. (Logstash는 `flat("_")`이라 `app_userId`처럼 **이름만 이어붙는다**.)

**접두어 안에서도 자바 타입은 그대로 보존된다** — 중첩이 값을 문자열로 만들지 않는다. 실행 확인:

```json
"app":{"userId":"42","traceId":"8f2c1a","method":"POST","route":"/api/attempts/{id}/submit",
       "status":502,"durationMs":41230,"ok":true,"ratio":0.75,"nil":null}
```

접두어는 **내장 ECS 이름과의 충돌을 확실히 막아준다**(단 MDC↔KVP 사이 중복은 못 막는다). 상세는 [아래 표](#contextprefix가-막아주는-충돌-못-막는-충돌).

`context.include: false`로 하면 MDC와 KeyValuePair가 **둘 다** 사라진다(실행 확인). 하나만 끄는 스위치는 없다.

### 그래도 KeyValuePair를 권하는 이유

- MDC는 `ThreadLocal`이라 `try/finally`로 `MDC.remove()` 하지 않으면 스레드 풀에서 다음 요청에 샌다.
- 값이 전부 `String`이라 SQL에서 `CAST`가 필요하다.
- traceId처럼 **요청 전체에 걸리는** 값은 MDC가 맞고(Micrometer tracing이 이미 그렇게 넣는다), 액세스 로그 한 줄에만 붙는 `status`/`durationMs`는 KeyValuePair가 맞다. 둘은 경쟁 관계가 아니라 스코프가 다르다.

### 키 이름 충돌 (실제 위험)

MDC 키와 KeyValuePair 키가 **서로** 겹치면:

```
IllegalStateException: Duplicate nested pairs added under 'route'
```

ECS가 **이미 쓰는 최상위 이름**과 겹치면:

```
IllegalStateException: The name 'message' has already been written
```

**항상 예약된 이름**: `@timestamp`, `log`, `process`, `service`, `message`, `ecs`
(`log.level`·`service.name`처럼 점으로 파고들어도 부모인 `log`/`service`에서 먼저 터진다.)

**조건부 예약**: `error`, `tags` — 이 둘은 이벤트에 각각 **예외가 있을 때**, **마커가 있을 때만** 기록된다. 그래서 예외 없는 이벤트에서는 `addKeyValue("error", ...)`가 **조용히 성공한다**:

```
R5: KVP 'tags', 마커 없음 → {"message":"Request","tags":"boom","ecs":{...}}   ← 통과
R3: KVP 'tags', 마커 있음 → !! The name 'tags' has already been written        ← 폭발
R1: KVP 'error', 예외 있음 → !! The name 'error' has already been written      ← 폭발
```

> ⚠️ 이건 무조건 예약된 것보다 **더 위험하다.** `error`라는 키는 평상시엔 멀쩡히 동작하다가 **예외를 함께 로깅하는 순간**, 즉 그 로그가 가장 필요한 순간에만 터진다. 개발 중엔 안 잡힌다.

**그리고 이게 예외 하나로 끝나지 않는다.** 실패한 인코딩이 스레드 로컬 버퍼를 되돌리지 않아서, **같은 스레드의 다음 로그 한 줄이 앞부분이 중복된 깨진 JSON으로 나간다.** 격리해서 재현한 결과:

```
good1: {"@timestamp":...,"route":"/a","ecs":{"version":"8.11"}}         ← 정상
bad  : !! IllegalStateException: The name 'message' has already been written
good2: {"@timestamp":...,"message":"Req",{"@timestamp":...,"route":"/b","ecs":{...}}   ← 깨짐
good3: {"@timestamp":...,"route":"/c","ecs":{"version":"8.11"}}         ← 이후 정상화
```

원인은 `org.springframework.boot.json.AppendableByteArray`다 — Javadoc에 "using a single cached buffer scoped to the thread"라고 적혀 있고 `ThreadLocal<SoftReference<AppendableByteArray>>` 캐시를 쓴다. 예외로 중단되면 버퍼가 정리되지 않는다.
<https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/json/AppendableByteArray.java>

### `context.prefix`가 막아주는 충돌, 못 막는 충돌

검사가 **최종 멤버 이름** 기준으로 돈다는 것을 실행으로 확인했다. 그래서 접두어를 걸면 `app.error`가 되어 최상위 `error`와 더는 겹치지 않는다.

| 충돌 종류 | prefix 없음 | `context.prefix: app` |
|---|---|---|
| KVP `error` + 예외 있는 이벤트 | **터짐** + 다음 줄 깨짐 | **안전** — `"app":{"error":"boom"}`와 `"error":{"type":...}`가 공존 |
| KVP `tags` + 마커 있는 이벤트 | **터짐** + 다음 줄 깨짐 | **안전** — `"app":{"tags":"boom"}`, `"tags":["AUDIT"]` |
| KVP `message` / `process` / `ecs` / `@timestamp` | **터짐** + 다음 줄 깨짐 | **안전** |
| KVP 키 == 접두어 이름 (`app`) | — | **안전** — `"app":{"app":"boom"}` |
| **MDC 키와 KVP 키가 서로 같음** (양쪽 다 `error`) | **터짐** | **여전히 터짐** — `Duplicate nested pairs added under 'app.error'`, 다음 줄도 깨짐 |

마지막 줄이 핵심이다. **MDC ↔ KeyValuePair 사이의 중복은 접두어로 막을 수 없다.** 둘 다 같은 접두어를 받아 같은 경로로 합쳐지기 때문이다.

실무 결론:

- **`context.prefix`는 내장 ECS 이름과의 충돌에 대해서는 완전한 방어책이다.** `error`/`tags`가 조건부 예약이라 테스트에서 안 잡히는 걸 감안하면, 접두어를 거는 편이 안전하다.
- 대신 **MDC 키와 KVP 키 이름이 겹치지 않게 하는 건 여전히 사람 몫이다.** traceId류(MDC)와 액세스 로그 필드(KVP)는 애초에 이름이 겹칠 일이 없으므로 실질 위험은 낮다.
- 접두어를 쓰면 SQL 경로가 전부 `app.status`처럼 한 단계 깊어진다. 그 비용과 맞바꾸는 것이다.
- 접두어를 안 쓸 거라면 `method`/`route`/`path`/`status`/`durationMs`는 확인된 안전 이름이다. `error`만은 쓰지 마라.

---

## 3. `StructuredLoggingJsonMembersCustomizer` — **된다** (이벤트 접근 가능)

### 인터페이스 전문

```java
package org.springframework.boot.logging.structured;

@FunctionalInterface
public interface StructuredLoggingJsonMembersCustomizer<T> {

    void customize(JsonWriter.Members<T> members);

    interface Builder<T> {              // @since 3.5.4
        default Builder<T> nested();
        Builder<T> nested(boolean nested);
        StructuredLoggingJsonMembersCustomizer<T> build();
    }
}
```

<https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/structured/StructuredLoggingJsonMembersCustomizer.java>

### 이벤트에 접근되는가 — **된다**

정적 멤버 라이터만 주는 게 아니다. `T`가 곧 `ILoggingEvent`이고, `Members<T>#add(String, Extractor<T,V>)`가 **이벤트를 받는 추출 함수**를 받는다. 그래서 이벤트별 값을 얼마든지 계산해 넣을 수 있다. 즉 원리적으로는 **이 커스터마이저로 KeyValuePair/MDC를 직접 승격시키는 것도 가능**하다 — 다만 1번이 이미 그 일을 해주므로 그럴 이유가 없다.

`Members<T>`의 관련 시그니처:

```java
public Member<T> add(String name);
public <V> Member<V> add(String name, @Nullable V value);         // 정적 값
public <V> Member<V> add(String name, Supplier<@Nullable V> supplier);
public <V> Member<V> add(String name, Extractor<T, V> extractor);  // ← 이벤트별 값
public Member<T> add();                                            // 이름 없음 = 최상위 병합
public void applyingPathFilter(Predicate<MemberPath> predicate);
public void applyingNameProcessor(NameProcessor nameProcessor);
```

### 동작하는 최소 예제 (실행 검증됨)

```java
package com.promptstudio.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;

public class AccessLogCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    private final Environment environment;

    public AccessLogCustomizer(Environment environment) {   // 생성자 주입됨
        this.environment = environment;
    }

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.add("kvpCount", (event) ->
                event.getKeyValuePairs() != null ? event.getKeyValuePairs().size() : 0);
        members.add("region", this.environment.getProperty("app.region", "unknown"));
    }
}
```

```yaml
logging:
  structured:
    json:
      customizer: com.promptstudio.logging.AccessLogCustomizer   # 콤마로 여러 개
```

실제 출력 꼬리:

```json
..."ecs":{"version":"8.11"},"kvpCount":3,"region":"ap-northeast-2"}
```

멤버는 **ECS 기본 멤버 뒤에 붙는다**(`ecs.version` 다음). 등록 경로는 위 프로퍼티 외에 `META-INF/spring.factories`의 `org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer` 키도 된다(인터페이스 Javadoc 명시).

생성자 주입 가능 타입은 Javadoc 기준 `Environment`, Logback일 때 추가로 `ch.qos.logback.classic.pattern.ThrowableProxyConverter`.

### 언제 쓰나

이벤트마다 **자동으로** 붙어야 하는 파생 값(모든 로그에 리전/인스턴스 id, 혹은 `event`에서 계산되는 값)에 쓴다. 호출부마다 다른 값은 1번이 훨씬 싸다.

---

## 4. 커스텀 `StructuredLogFormatter` — **된다** (탈출구, 확인 완료)

### FQN이 먹히는가 — 먹힌다

`StructuredLogFormatterFactory#get(String format)`이 common format id로 못 찾으면 `getUsingClassName(format)`으로 넘어간다. 문서도 명시한다:

> "To enable your custom format, set the property `logging.structured.format.console` or `logging.structured.format.file` to the fully qualified class name of your implementation."

실행으로도 확인했다 — `logging.structured.format.file=MyFormatter`로 진짜 인스턴스가 만들어지고 출력이 바뀌었다.

### 구현해야 하는 것

```java
package org.springframework.boot.logging.structured;

@FunctionalInterface
public interface StructuredLogFormatter<E> {
    String format(E event);
    default byte[] formatAsBytes(E event, Charset charset) { ... }
}
```

**타입 인자는 반드시 `ILoggingEvent`여야 한다.** 팩토리가 `GenericTypeResolver`로 검사하고 다르면 즉시 실패한다:

```java
Assert.state(this.logEventType.equals(typeArgument),
    () -> "Type argument of %s must be %s but was %s"...);
```

### 생성자 주입 가능 타입 (Javadoc 전문)

`StructuredLogFormatter` Javadoc이 목록을 준다 — 문서가 "JavaDoc을 보라"고만 하고 넘어간 부분이다:

| 타입 | 비고 |
|---|---|
| `Environment` | |
| `StructuredLoggingJsonMembersCustomizer` | |
| `StructuredLoggingJsonMembersCustomizer.Builder` | |
| `StackTracePrinter` | **null일 수 있음** |
| `ContextPairs` | |
| `ch.qos.logback.classic.pattern.ThrowableProxyConverter` | Logback 전용 |

<https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/structured/StructuredLogFormatter.java>

실행 검증한 생성자:

```java
public class MyFormatter implements StructuredLogFormatter<ILoggingEvent> {
    public MyFormatter(Environment environment, ContextPairs contextPairs,
                       ThrowableProxyConverter throwableProxyConverter) { ... }
    @Override public String format(ILoggingEvent event) { return "...\n"; }
}
```

→ 세 개 모두 non-null로 주입됐다. 등록은 프로퍼티 한 줄이 전부고 `spring.factories`도 빈 등록도 필요 없다. **개행은 직접 붙여야 한다**(`format`이 반환한 문자열이 그대로 나간다).

### 비용

ECS 포매터 클래스는 **package-private**이라 상속해서 살짝 고칠 수 없다. 처음부터 다 쓰거나, `JsonWriterStructuredLogFormatter`(public)를 상속해 멤버 정의를 통째로 다시 써야 한다. 그래서 **1번·3번으로 안 되는 경우에만** 갈 길이다. 지금 요구사항은 1번으로 끝난다.

---

## 5. 그 외 4.1이 주는 것

- **`logging.structured.json.rename.*`** — **KeyValuePair/MDC로 들어온 이름에도 적용된다.** 실행 확인: `rename.durationMs=duration_ms` → `"duration_ms":41230`. 코드를 안 고치고 JSON 키만 스네이크 케이스로 맞출 때 유용하다.
  **단, 대상은 원본 키가 아니라 최종 경로다.** `context.prefix: app`을 걸었다면 `rename.durationMs`는 **아무 일도 하지 않고**, `rename.app.durationMs=duration_ms`라고 써야 먹는다(실행 확인). `rename.app=ctx`처럼 접두어 객체 자체의 이름도 바꿀 수 있다 → `"ctx":{"durationMs":41230}`.
- **`logging.structured.json.include` / `exclude`** — 경로 단위 필터. `exclude: process.thread.name` 확인(단, 부모 객체는 `"thread":{}`로 **빈 객체가 남는다**).
- **`logging.structured.json.stacktrace.*`** — `root`(first/last), `max-length`, `max-throwable-depth`, `include-common-frames`, `include-hashes`, `printer`. 스택트레이스 길이를 자를 수 있다.
- **Markers → `tags`** — `Marker`를 달면 ECS의 `tags` 배열로 나간다(중첩 마커까지 평탄화, `TreeSet`이라 정렬됨). 필드가 아니라 **라벨**이 필요할 때 쓸 수 있는 네 번째 경로다.
- 4.1 릴리스 노트에 **구조화 로깅 관련 신규 항목은 없다.** 로깅 항목은 Log4j 파일 로테이션뿐이다. 이 기능은 3.4에 들어와 3.5(`ContextPairs`, `Builder`)에서 다듬어진 뒤 4.1까지 그대로다.

---

## 6. 콘솔 평문 쪽 — `%kvp`

파일이 JSON이 되어도 **콘솔은 여전히 `PatternLayout`**이다. 여기서 KeyValuePair를 보여주는 건 Boot가 아니라 **Logback의 `%kvp` 변환어**다(logback-classic 1.5.34에 `kvp`, `maskedKvp` 둘 다 등록되어 있다).

### `%msg`만으로는 필드가 사라진다 — 실측

레포 현행 패턴(`backend/src/main/resources/application.yml`) 그대로 렌더링한 결과:

```
... [req:r-77] com.promptstudio.web.AccessLog : Request
```

`method`/`route`/`status`/`durationMs`가 **전부 안 보인다.** `%msg`는 `getFormattedMessage()`뿐이고 KeyValuePair는 별개 필드이기 때문이다. **필드를 메시지 문자열에서 key-value로 옮기면 콘솔은 반드시 같이 고쳐야 한다.**

### `%kvp` 옵션 (실측)

| 패턴 | 출력 |
|---|---|
| `%kvp` (기본) | `method="POST" status="502"` |
| `%kvp{DOUBLE}` | `method="POST" status="502"` (기본과 동일) |
| `%kvp{SINGLE}` | `method='POST' status='502'` |
| `%kvp{NONE}` | `method=POST status=502` |
| `%kvp{아무거나}` | `DOUBLE`로 조용히 폴백 |

- **구분자는 공백 하나**, 형식은 `key=value`, 순서는 `addKeyValue` 호출 순서.
- **`null` 값은 문자열 `null`로 렌더된다** — `nil="null"` / `nil=null`.
- `NONE`은 **값에 공백이 있어도 이스케이프하지 않는다**(`withSpace=a b`). 값에 공백이 섞일 수 있으면 기본(`DOUBLE`)이 안전하다.
- `%maskedKvp`는 **옵션 없이 쓰면 NPE로 죽는다**(`Cannot invoke "java.util.List.contains(Object)" because "this.maskList" is null`). `%maskedKvp{status,route}`처럼 마스킹할 키를 줘야 하고, 해당 값이 `XXX`로 바뀐다.

### `%kvp`는 MDC를 찍지 않는다 — 중복 출력 없음

역할이 완전히 갈린다(실측):

```
kvp=[method=POST route=/api/attempts/{id}/submit status=502 durationMs=41230]   ← KeyValuePair만
mdcAll=[requestId=r-77]                                                         ← %X, MDC만
```

`requestId`는 MDC라서 `%X{requestId}`로만 나오고 `%kvp`에는 안 섞인다. **콘솔에서 두 번 찍힐 걱정은 없다.**

> 단 **파일(JSON)에서는 반대다** — ECS는 MDC와 KeyValuePair를 같은 자리에 병합하므로 `requestId`가 다른 필드들과 나란히 들어간다. 콘솔은 분리, 파일은 병합이다.

### `context.prefix`는 콘솔에 영향이 없다

구조화 로깅 프로퍼티라 `PatternLayout`과는 무관하다. `context.prefix: app`을 켠 채 같은 이벤트를 양쪽으로 뽑은 결과:

```
[콘솔]  ... : Request | method=POST route=/api/attempts/{id}/submit status=502 durationMs=41230
[파일]  ..."message":"Request","app":{"requestId":"r-77","method":"POST",...,"durationMs":41230},...
```

콘솔은 **평평하게 그대로**, 파일만 `app` 아래로 들어간다. 접두어가 패턴 출력으로 새지 않는다.

### 권장 콘솔 패턴

문제는 **콘솔 패턴이 모든 로그에 공통**이라는 점이다. KeyValuePair가 없는 일반 로그(기동 로그 등)에서 구분자가 덩그러니 남는다:

```
'%msg %kvp{NONE}'   KVP 없음 → [Request ]        ← 후행 공백만, 무해
'%msg | %kvp{NONE}' KVP 없음 → [Request | ]      ← 파이프가 매 줄 남음, 지저분
```

지금 콘솔 모양(`Request | method=...`)을 그대로 유지하면서 일반 로그도 깨끗하게 두려면 `%replace`로 빈 경우의 구분자를 지우면 된다. **`application.yml`에 넣을 형태로 파싱까지 검증했다**:

```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %5p ${PID:-} --- [%thread] [req:%X{requestId:-}] %logger{39} : %replace(%msg | %kvp{NONE}){' \\| $', ''}%n"
```

(YAML 큰따옴표 안이라 `\\|`로 써야 Logback이 `\|`로 받는다. 작은따옴표 스타일도 같은 결과다.)

렌더 결과 — 액세스 로그는 기존 모양 유지, 일반 로그는 잔여물 없음:

```
... AccessLog : Request | method=POST route=/api/attempts/{id}/submit status=502 durationMs=41230
... AccessLog : Started BackendApplication in 4.2 seconds
```

`|`에 집착하지 않는다면 **`%msg %kvp{NONE}`이 제일 싸다**(후행 공백 하나만 남는다). 값에 공백이 들어갈 수 있으면 `{NONE}`을 빼고 기본 인용을 쓰면 된다.

---

## 확인 요청 항목 답변

### `logging.structured.json.add.*`는 정적 값만인가 — **그렇다**

타입이 `Map<String, String>`이다. jar의 `spring-configuration-metadata.json`과 레코드 선언 양쪽에서 확인:

```java
record StructuredLoggingJsonProperties(Set<String> include, Set<String> exclude,
        Map<String, String> rename, Map<String, String> add, ...)
```

값이 `String` 리터럴이라 **이벤트별 값을 넣을 방법이 없다.** 실행해도 `"corpname":"mycorp"`가 매 줄 동일하게 붙을 뿐이다. **이 문제는 `add`로 풀 수 없다.**

참고로 ECS에서는 `add`도 `nested` 모드로 처리돼서 `logging.structured.json.add.a.b=x`가 `{"a":{"b":"x"}}`가 된다(`StructuredLoggingJsonPropertiesJsonMembersCustomizer`).

### 콘솔과 파일에 다른 포맷을 동시에 줄 수 있는가 — **그렇다**

프로퍼티가 애초에 분리돼 있다(`LoggingSystemProperty`):

```java
CONSOLE_STRUCTURED_FORMAT("CONSOLE_LOG_STRUCTURED_FORMAT", "logging.structured.format.console"),
FILE_STRUCTURED_FORMAT("FILE_LOG_STRUCTURED_FORMAT", "logging.structured.format.file"),
```

그리고 `DefaultLogbackConfiguration#createEncoder`가 **appender 종류별로 따로** 판정한다:

```java
private Encoder<ILoggingEvent> createEncoder(LogbackConfigurator config, String type) {  // type = CONSOLE | FILE
    String structuredLogFormat = resolve(config, "${" + type + "_LOG_STRUCTURED_FORMAT}");
    if (StringUtils.hasLength(structuredLogFormat)) {
        return createStructuredLogEncoder(structuredLogFormat);   // JSON
    }
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setPattern(resolve(config, "${" + type + "_LOG_PATTERN}"));   // ← 평문 패턴
    return encoder;
}
```

<https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/DefaultLogbackConfiguration.java>

**콘솔 포맷을 비워두면 콘솔은 `PatternLayoutEncoder` + `${CONSOLE_LOG_PATTERN}`으로 간다.** 즉 평소의 컬러 평문(`logging.pattern.console`로 바꾸는 그것) 그대로다. 원하는 조합이 정확히 성립한다.

> ⚠️ **전제**: 파일 appender는 `logging.file.name` 또는 `logging.file.path`가 있어야 생성된다(`DefaultLogbackConfiguration#apply`에서 `logFile != null`일 때만). 둘 다 없으면 `logging.structured.format.file=ecs`는 **조용히 아무 일도 하지 않는다.** 지금 배포가 stdout(`docker logs`)만 쓰고 있으므로 여기서 갈린다 — 파일을 만들 생각이 없다면 `format.console=ecs`로 가야 하고, 그러면 콘솔 평문은 포기해야 한다.

---

## ECS JSON 멤버 이름 (SQL 작성용)

`ElasticCommonSchemaStructuredLogFormatter#jsonMembers` 정의 순서 그대로다. 실행 출력과 대조 완료.

| 내용 | **정확한 멤버 경로** | 타입 | 비고 |
|---|---|---|---|
| 타임스탬프 | `@timestamp` | string | `Instant` 직렬화. 예: `2026-08-01T04:15:00.067462Z` (UTC, `Z`) |
| 레벨 | `log.level` | string | `INFO`/`WARN`/`ERROR`. **`level` 아니다** |
| 로거 이름 | `log.logger` | string | **`logger_name` 아니다** |
| PID | `process.pid` | number | `spring.application.pid` 있을 때만 |
| 스레드 | `process.thread.name` | string | **`thread` 아니다** |
| 서비스명 | `service.name` | string | `logging.structured.ecs.service.name` → 없으면 `spring.application.name` |
| 서비스 버전 | `service.version` | string | → 없으면 `spring.application.version` |
| 환경 | `service.environment` | string | `logging.structured.ecs.service.environment` |
| 노드명 | `service.node.name` | string | `logging.structured.ecs.service.node-name` |
| 메시지 | `message` | string | `getFormattedMessage()` (플레이스홀더 치환 후) |
| **MDC 항목** | **`<키>`** (최상위) | string | 값은 항상 문자열 |
| **KeyValuePair** | **`<키>`** (최상위) | **원래 타입** | number/boolean/null 보존 |
| 예외 타입 | `error.type` | string | FQCN |
| 예외 메시지 | `error.message` | string | |
| 스택트레이스 | `error.stack_trace` | string | **개행 포함 단일 문자열** |
| 마커 | `tags` | array&lt;string&gt; | 없으면 멤버 자체가 생략 |
| ECS 버전 | `ecs.version` | string | 항상 `"8.11"` |

주의점:

- **`level`이 아니라 `log.level`**, **`timestamp`가 아니라 `@timestamp`**다. 이름 앞의 `@`는 SQL에서 인용이 필요할 수 있다.
- `service.node`는 `node-name`을 안 줘도 **빈 객체 `{}`로 남는다**(실행 확인). `service.node.name`은 없을 수 있다.
- `error.*`는 예외가 없으면 **멤버 전체가 없다**. `tags`도 마찬가지. SQL에서는 NULL 처리 필요.
- `process.pid`는 `spring.application.pid`가 있을 때만 나온다.

### `@timestamp` 형식 — **소수부 자릿수가 가변이다** (Postgres 로더 주의)

포매터는 `members.add("@timestamp", ILoggingEvent::getInstant)`로 **`Instant`를 그대로** 넘긴다. 포맷 훅도, 관련 프로퍼티도 없다(Logstash 포맷만 `.as(...)`로 변환한다). 그래서 값은 `Instant.toString()` 규칙을 따른다 — **소수부가 0/3/6/9자리로 가변**이고, 자리가 남으면 그룹째 생략된다.

**진짜 로거를 통과시킨 실제 출력**(`log.atInfo().addKeyValue(...).log("Request")` 5회):

```
"@timestamp":"2026-08-01T10:33:41.788054908Z"
"@timestamp":"2026-08-01T10:33:41.798945572Z"
"@timestamp":"2026-08-01T10:33:41.803863865Z"
"@timestamp":"2026-08-01T10:33:41.807445853Z"
"@timestamp":"2026-08-01T10:33:41.811560393Z"
```

Logback 1.5는 나노초 시계를 쓰므로 **평소엔 9자리**다. 다만 나노값 끝이 0으로 떨어지면 짧아진다:

| 이벤트 시각 | 출력 리터럴 |
|---|---|
| 정각 | `2026-08-01T00:00:00Z` (소수부 **없음**) |
| 1ns | `2026-08-01T00:00:00.000000001Z` |
| 1ms | `2026-08-01T00:00:00.001Z` |
| 0.5s | `2026-08-01T00:00:00.500Z` |

**항상 UTC `Z`다.** 로컬 오프셋은 나오지 않는다(`TZ=Asia/Seoul`에서 실행해도 `Z`). `logging.pattern.dateformat`을 설정해도 **JSON에는 영향이 없다**(그건 `PatternLayout` 전용). ECS 서비스 프로퍼티도 무관하다. 실행으로 전부 확인했다.

**`@timestamp`는 모든 이벤트에 무조건 나온다** — 조건 걸이(`when...`)가 없다. 메시지가 `null`이고 MDC·KVP가 비어 있는 최소 이벤트에서도 나온다:

```json
{"@timestamp":"2026-08-01T00:00:00Z","log":{"level":"ERROR","logger":"L"},...,"message":null,"ecs":{"version":"8.11"}}
```

> ⚠️ **자릿수가 가변이라 문자열 정렬 ≠ 시간순 정렬이다.** `Z`(0x5A)가 `.`(0x2E)보다 커서 정각 값이 같은 초의 소수부 값들보다 **뒤로** 밀리고, `.001000002Z`가 `.001Z`보다 **앞으로** 온다. Postgres 18.4에서 실측:
>
> ```
> ORDER BY raw->>'@timestamp'                    ORDER BY (raw->>'@timestamp')::timestamptz
>   ...T00:00:00.000000001Z                        ...T00:00:00Z
>   ...T00:00:00.001000002Z                        ...T00:00:00.000000001Z
>   ...T00:00:00.001Z                              ...T00:00:00.001Z
>   ...T00:00:00Z          ← 정각이 꼴찌            ...T00:00:00.001000002Z
> ```
>
> **`text`가 아니라 `timestamptz`로 저장/정렬해야 한다.** 위 리터럴 5종 모두 Postgres가 문제없이 파싱한다.
>
> 단 **Postgres `timestamptz`는 마이크로초 해상도라 나노초가 사라진다**(실측): `.067462556Z` → `.067463`(반올림), `.000000001Z` → `.000000`. 1ns 차이로 발생한 두 이벤트는 캐스팅 후 **같은 값이 되어 순서가 보장되지 않는다.** 정렬 안정성이 필요하면 보조 키(시퀀스/`ctid`)를 같이 써야 한다.

### 스택트레이스 형태 — **구조화되지 않은 단일 문자열**

`error.stack_trace` 하나에 개행(`\n`)과 탭이 그대로 들어간 문자열이다. 프레임 배열이 아니다. 실제 출력:

```json
"stack_trace":"java.lang.IllegalStateException: upstream 502\n\tat EcsProbe.event(EcsProbe.java:52)\n\tat EcsProbe.run(EcsProbe.java:66)\n"
```

JSON 이스케이프가 되므로 **한 줄(JSON Lines)은 유지된다** — 로그 한 줄 = 레코드 한 개가 깨지지 않는다. `logging.structured.json.stacktrace.max-length`로 길이를 자를 수 있다.

### 전체 예제 (MDC + KeyValuePair + 예외, 실제 출력)

MDC `traceId=8f2c1a`, `userId=42` / KeyValuePair `method`,`route`,`status`,`durationMs` / `IllegalStateException`:

```json
{
  "@timestamp": "2026-08-01T04:15:00.067462Z",
  "log": { "level": "INFO", "logger": "com.promptstudio.web.AccessLog" },
  "process": { "pid": 12345, "thread": { "name": "http-nio-8080-exec-3" } },
  "service": {
    "name": "ssafy-be", "version": "1.0.0", "environment": "prod",
    "node": { "name": "ec2-a" }
  },
  "message": "Request",
  "userId": "42",
  "traceId": "8f2c1a",
  "method": "POST",
  "route": "/api/attempts/{id}/submit",
  "status": 502,
  "durationMs": 41230,
  "error": {
    "type": "java.lang.IllegalStateException",
    "message": "upstream 502",
    "stack_trace": "java.lang.IllegalStateException: upstream 502\n\tat ...\n"
  },
  "ecs": { "version": "8.11" }
}
```

MDC와 KeyValuePair가 **구분 없이 나란히** `message` 뒤에 온다. 나중에 어느 쪽에서 왔는지 JSON만 보고는 알 수 없다.

### 참고: 다른 포맷의 같은 이벤트

포맷을 바꾸면 배치가 달라진다. Logstash는 **평탄화 + `_` 결합**, GELF는 **`_` 접두어**다(둘 다 실행 확인).

```json
// logstash
{"@timestamp":"2026-08-01T13:15:00.067462+09:00","@version":"1","message":"Request",
 "logger_name":"...","thread_name":"...","level":"INFO","level_value":20000,
 "userId":"42","traceId":"8f2c1a","method":"POST","route":"...","status":502,"durationMs":41230,
 "stack_trace":"..."}

// gelf
{"version":"1.1","short_message":"Request","timestamp":1785557700.067,"level":6,
 "_level_name":"INFO","_process_pid":12345,"_process_thread_name":"...","host":"probe",
 "_log_logger":"...","_userId":"42","_traceId":"8f2c1a","_method":"POST","_route":"...",
 "_status":502,"_durationMs":41230,
 "full_message":"...","_error_type":"...","_error_stack_trace":"...","_error_message":"..."}
```

Logstash가 타임스탬프에 **로컬 오프셋**(`+09:00`)을 쓰는 반면 ECS는 UTC `Z`인 점이 다르다. GELF의 `timestamp`는 epoch 초(소수점 밀리초)다.

---

## 미확인 목록

1. **`labels.*` 관련 Boot의 의도** — ECS 스펙은 커스텀 필드를 `labels.*`에 두라고 하지만 Boot는 최상위에 둔다. 이게 의도된 설계인지 스펙 위반인지에 대한 Spring 측 서술을 1차 자료에서 찾지 못했다. **동작 자체는 실행으로 확정**했으므로 실무에는 영향 없다. → **의도 미확인**.
2. **실패 인코딩 후 다음 줄 오염이 알려진 버그인지** — `AppendableByteArray`의 스레드 로컬 버퍼가 예외 시 정리되지 않아 다음 줄이 깨지는 것을 재현했으나, 이에 해당하는 spring-boot 이슈를 검색해 확인하지는 않았다. 실제 Logback appender 경로에서도 동일한지는 격리 harness가 아닌 **구동 중인 애플리케이션으로는 확인하지 못했다**(`StructuredLogEncoder.encode`를 직접 호출한 결과다 — Boot도 같은 메서드를 쓰지만 appender가 예외를 어떻게 처리하는지는 별개). → **부분 미확인**.
3. **Log4j2를 쓸 때의 동작** — 이 문서는 전부 Logback 경로다. `org.springframework.boot.logging.log4j2`에도 동명의 ECS 포매터가 있고 소스상 구조가 같아 보이나, **실행 검증은 하지 않았다.** 이 레포는 `spring-boot-starter-logging`(Logback)이라 해당 없음. → **미확인**.
4. **`logging.structured.json.context.include` 기본값의 문서 전거** — 레퍼런스 문서와 프로퍼티 부록에 `context.*` 항목 서술이 없고, jar 메타데이터의 `defaultValue`도 비어 있다. 기본 `true`는 **코드**(`new Context(true, null)`)와 실행으로만 확인했다. → **문서 전거 없음, 동작은 확정**.
5. **`spring.factories` 등록 경로** — 인터페이스 Javadoc에 적힌 대로 인용했으나 실행으로 검증한 것은 `logging.structured.json.customizer` 프로퍼티 경로뿐이다. → **미확인**.
6. **커스터마이저 다중 등록 시 순서** — 프로퍼티에 콤마로 여러 개를 줄 수 있다는 것까지는 타입(`Set<Class<...>>`)으로 확인했으나, `Set`이라 **적용 순서가 보장되는지는 확인하지 못했다.** 서로 다른 이름만 추가한다면 무관하다. → **미확인**.

---

## 참고 링크

- Logging (Structured Logging): <https://docs.spring.io/spring-boot/4.1/reference/features/logging.html>
- Common Application Properties: <https://docs.spring.io/spring-boot/appendix/application-properties/index.html>
- SLF4J Fluent Logging API: <https://www.slf4j.org/manual.html#fluent>
- 소스 태그 (모든 클래스는 `core/spring-boot/src/main/java/` 아래): <https://github.com/spring-projects/spring-boot/tree/v4.1.0>
  - `logging/logback/ElasticCommonSchemaStructuredLogFormatter.java`
  - `logging/logback/DefaultLogbackConfiguration.java`, `logging/logback/StructuredLogEncoder.java`
  - `logging/structured/ContextPairs.java`, `logging/structured/StructuredLogFormatter.java`
  - `logging/structured/StructuredLogFormatterFactory.java`
  - `logging/structured/StructuredLoggingJsonMembersCustomizer.java`
  - `logging/structured/StructuredLoggingJsonProperties.java`
  - `json/JsonWriter.java`, `json/AppendableByteArray.java`
- Spring Boot 4.1 Release Notes (구조화 로깅 항목 없음): <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes>
- 관련 문서: [`spring-boot-4.1-observability.md`](./spring-boot-4.1-observability.md)
