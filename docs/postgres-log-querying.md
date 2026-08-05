# PostgreSQL로 애플리케이션 로그를 SQL 질의하기

> 목적: 파일에 남는 JSON-lines 애플리케이션 로그를 **PostgreSQL에 실어 SQL로 조사한다.**
> 전제: 로그는 **런타임에 계속 파일로 쓴다**(원본은 파일이고 DB가 죽어도 남아야 한다). PostgreSQL은 **질의 표면**일 뿐이며, 사후에 파일에서 채운다.
> 대상: `postgres:18.4` (`docker-compose.yml`의 `promptstudio-postgres`, 서비스 실데이터 보유). 백엔드는 Jenkins가 띄우는 **별도 컨테이너**로 `backend.log` + Logback 회전·gzip.
> 운영 현실: EC2 한 대, 학생 팀, **주 몇 회** 사용자 제보를 받은 뒤에 조사. 상시 관측이 아니다.

> ⚠️ **검증 방식 고지.** 이 문서의 모든 사실은 PostgreSQL 18 공식 문서 / 17·18 릴리스 노트 / PostgreSQL·Spring Boot 소스·커밋 메시지 / Docker 공식 문서 / RFC 8259 **1차 자료**로 확인했다.
> 그러나 **실행해서 확인한 명령은 하나도 없다.** 이 작업트리에는 `psql` 클라이언트가 없고(`command not found`), `docker ps -a`가 비어 있어 **띄워진 Postgres 컨테이너가 없다**. 명령을 돌려본 것처럼 읽히면 안 된다. 실행이 필요한 항목은 전부 맨 아래 [미확인 목록](#미확인-목록)에 모았고, 각각 **확인용 한 줄**을 같이 적었다.

---

## 권장안 — 순서대로 실행하는 것

결론부터. 설계 근거는 §A~§D에 있다.

| | 선택 | 이유 |
|---|---|---|
| 적재 방법 | `\copy ... FROM PSTDIN` + `FORMAT csv` + 제어문자 구분자 | JSON 바이트가 절대 트리거할 수 없는 유일한 조합 (§A-1) |
| 컨테이너 통과 | `docker exec -i` 파이프 | 파일을 postgres 컨테이너에 마운트하지 않아도 된다 (§A-2) |
| 테이블 모양 | **staging(raw만) → INSERT…SELECT → 실제 컬럼** | PG 18에서 생성 컬럼은 기본 VIRTUAL이라 **인덱스를 못 만든다** (§B-1) |
| 격리 | 같은 인스턴스의 **별도 데이터베이스** | `pg_dump`가 DB 단위라 백업에 안 섞인다. 잊어버릴 여지가 없다 (§C-1) |
| 보존 | **적재 스크립트가 매번 TRUNCATE** | 크론이 필요 없고, 중복 적재 문제도 같이 사라진다 (§C-2, §C-3) |
| 앱이 DB에 직접 쓰기 | **하지 않는다** | DB가 죽은 순간의 로그가 사라진다 — 가장 조사하고 싶은 순간이다 (§D) |

### 0단계 — 전제 확인

이 문서는 로그 파일이 **JSON-lines(ECS)** 라고 가정한다. `application.yml`에 아직 없다 (2026-08-01 기준 `logging.pattern.console`만 있다). 먼저 이게 켜져야 한다:

```yaml
logging:
  file:
    name: ${LOG_FILE:/var/log/app/backend.log}
  structured:
    format:
      file: ecs          # 파일만 JSON. console은 지정하지 않아 평문 유지
  logback:
    rollingpolicy:
      total-size-cap: 2GB   # 기본 0B(무제한) — EC2 디스크 보호에 필수
```

그리고 **호스트에 볼륨으로 빼는 것을 강력히 권한다.** 컨테이너 안에만 있으면 재생성 때 로그가 사라지고, 적재할 때마다 컨테이너 두 개를 파이프로 이어야 한다(§A-2 (b)).

한 줄이 실제로 어떤 모양인지부터 눈으로 본다 — 아래 SQL의 JSON 경로가 전부 여기에 달려 있다:

```bash
head -1 /var/log/app/backend.log | jq .
```

기대 모양 (Spring Boot 4.1 ECS 포맷터 소스로 확인. `log`·`process`·`error`·`ecs`는 **중첩 객체**이고, MDC 키는 점이 없으면 **최상위**로 나온다):

```json
{
  "@timestamp": "2026-08-01T06:04:05.123Z",
  "log":     { "level": "ERROR", "logger": "com.promptstudio.global.logging.RequestLogFilter" },
  "process": { "pid": 1, "thread": { "name": "http-nio-9090-exec-3" } },
  "service": { "name": "backend", "environment": "prod" },
  "message": "Request failed | method=POST path=/api/attempts/42/submit status=500 durationMs=1204",
  "requestId": "3f9a1c2e-8b7d-4e21-9c14-5a2f6d8e0b93",
  "error":   { "type": "...", "message": "...", "stack_trace": "..." },
  "ecs":     { "version": "8.11" }
}
```

### 1단계 — 로그 전용 데이터베이스 (한 번만)

```bash
docker exec promptstudio-postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  -c 'CREATE DATABASE promptstudio_logs'
```

### 2단계 — DDL (한 번만)

```bash
docker exec -i promptstudio-postgres \
  psql -U "$POSTGRES_USER" -d promptstudio_logs -v ON_ERROR_STOP=1 <<'SQL'
-- 파일을 한 줄씩 그대로 받는 적재용 테이블. COPY의 유일한 대상이다.
-- NOT NULL을 걸지 않는다 — 빈 줄이 NULL로 들어오는데 ON_ERROR ignore가 그건 못 잡는다(§A-4).
CREATE UNLOGGED TABLE log_stage (
    raw jsonb
);

-- 질의용 본 테이블. 전부 '실제' 컬럼이다. 생성 컬럼이 아니다 — 이유는 §B-1.
-- 승격하는 필드는 '걸러내거나·정렬하거나·묶는' 6개뿐이다.
-- message·logger·thread·stack_trace는 승격하지 않고 raw에서 꺼내 읽는다(§C-4 용량).
CREATE UNLOGGED TABLE app_log (
    ts          timestamptz NOT NULL,
    level       text        NOT NULL,
    request_id  text,
    route       text,
    status      int,
    duration_ms int,
    raw         jsonb       NOT NULL
);

CREATE INDEX app_log_ts_idx          ON app_log (ts);
CREATE INDEX app_log_level_ts_idx    ON app_log (level, ts);
CREATE INDEX app_log_reqid_idx       ON app_log (request_id text_pattern_ops);
CREATE INDEX app_log_route_ts_idx    ON app_log (route, ts)      WHERE status >= 400;
CREATE INDEX app_log_status_ts_idx   ON app_log (status, ts)     WHERE status IS NOT NULL;
CREATE INDEX app_log_duration_idx    ON app_log (duration_ms DESC) WHERE duration_ms IS NOT NULL;
SQL
```

### 3단계 — 적재 스크립트

`scripts/load-logs.sh`. **매 실행마다 TRUNCATE부터 한다** — 그래서 두 번 돌려도 중복이 없고, 테이블이 "마지막 적재분"보다 커질 수 없다.

```bash
#!/usr/bin/env bash
# 로그 파일 → promptstudio_logs DB. 실행할 때마다 기존 내용을 버리고 새로 채운다.
# 사용: ./scripts/load-logs.sh [최근 N일치 회전 파일도 포함, 기본 1]
set -euo pipefail
shopt -s nullglob

PG_CONTAINER=promptstudio-postgres
LOG_DIR=${LOG_DIR:-/var/log/app}
LOG_FILE=$LOG_DIR/backend.log
DAYS=${1:-1}

pg() { docker exec -i "$PG_CONTAINER" psql -U "$POSTGRES_USER" -d promptstudio_logs -v ON_ERROR_STOP=1 -q "$@"; }

# JSON 텍스트에 절대 나올 수 없는 제어문자 두 개를 인용자/구분자로 쓴다 (§A-1)
COPY_OPTS="WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x01', ON_ERROR ignore, ENCODING 'UTF8')"

pg -c 'TRUNCATE log_stage, app_log'

# 현재 파일 + 최근 N일치 회전 gz를 한 스트림으로 흘려 넣는다.
# sed -n '/^{/p' 는 빈 줄과 JSON이 아닌 잡음(JVM 크래시 출력 등)을 미리 떨군다.
#   grep이 아니라 sed인 이유: 매칭이 0건이면 grep은 exit 1이고, pipefail이 켜져 있어
#   로그가 비어 있는 날에 스크립트가 통째로 죽는다. sed는 항상 0을 낸다.
{
  cat "$LOG_FILE"
  rotated=( $(find "$LOG_DIR" -name 'backend.log.*.gz' -mtime "-$DAYS") )
  if (( ${#rotated[@]} )); then zcat "${rotated[@]}"; fi
} | sed -n '/^{/p' \
  | pg -c "\\copy log_stage(raw) FROM PSTDIN $COPY_OPTS"

pg <<'SQL'
INSERT INTO app_log (ts, level, request_id, route, status, duration_ms, raw)
SELECT
    (raw ->> '@timestamp')::timestamptz,
    raw -> 'log' ->> 'level',
    raw ->> 'requestId',
    -- route·status·durationMs가 JSON 필드로 나오면 그걸 쓰고,
    -- 아직 message 안의 key=value 형태라면 정규식으로 꺼낸다. 둘 다 대응한다.
    COALESCE(raw ->> 'route',  substring(raw ->> 'message' from 'route=(\S+)')),
    COALESCE(raw ->> 'status', substring(raw ->> 'message' from 'status=(\d+)'))::int,
    COALESCE(raw ->> 'durationMs', substring(raw ->> 'message' from 'durationMs=(\d+)'))::int,
    raw
FROM log_stage
WHERE raw IS NOT NULL;

TRUNCATE log_stage;          -- 스테이징이 차지한 디스크를 바로 돌려준다
ANALYZE app_log;
SELECT count(*) AS loaded, min(ts) AS oldest, max(ts) AS newest FROM app_log;
SQL
```

> 로그가 **백엔드 컨테이너 안에만** 있다면 첫 파이프를 이렇게 바꾼다 (§A-2 (b)):
> ```bash
> docker exec promptstudio-backend cat /var/log/app/backend.log | sed -n '/^{/p' | pg -c "\\copy ..."
> ```

### 4단계 — 조사

```bash
docker exec -it promptstudio-postgres psql -U "$POSTGRES_USER" -d promptstudio_logs
```

질의는 §B-4에.

### 정리

```bash
docker exec promptstudio-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c 'DROP DATABASE promptstudio_logs'
```

**원본이 파일이므로 이 명령은 언제나 안전하다.** 다시 만들면 그만이다. 이게 별도 DB를 쓰는 가장 큰 이득이다.

---

## A. JSON-lines 파일을 PostgreSQL에 넣기

### A-1. 인용 안전한 적재 — 왜 `FORMAT csv` + 제어문자인가

**정답:**

```sql
\copy log_stage(raw) FROM PSTDIN WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x01', ON_ERROR ignore, ENCODING 'UTF8')
```

#### `FORMAT text`(기본값)를 쓰면 안 된다

가장 먼저 짚어야 할 함정이다. COPY의 기본 포맷인 `text`는 **백슬래시 이스케이프를 해석한다.** JSON 문자열 안에는 `\n`, `\"`, `\\`, `\uXXXX`가 정상적으로 들어 있고(스택트레이스는 통째로 `\n`으로 접혀 있다), text 포맷은 이걸 **디코드해 버린다.** 로그 한 줄이 조용히 여러 줄로 부서지거나 백슬래시가 사라진다. text 포맷에는 이 해석을 끄는 옵션이 없다. → **CSV만 쓸 수 있다.**

#### QUOTE·DELIMITER가 받는 값

<https://www.postgresql.org/docs/18/sql-copy.html> 원문:

> `DELIMITER`: "Specifies the character that separates columns within each row (line) of the file. The default is a tab character in text format, a comma in `CSV` format. **This must be a single one-byte character.**"
>
> `QUOTE`: "Specifies the quoting character to be used when a data value is quoted. The default is double-quote. **This must be a single one-byte character.** This option is allowed only when using `CSV` format."

즉 **1바이트여야 한다.** 인쇄 가능 문자 중에는 안전한 후보가 **하나도 없다** — 쉼표·탭·따옴표·백슬래시·파이프 전부 JSON 문자열 안에 그대로 들어올 수 있다. 질문에서 지적한 대로 **탭도 안 된다**(JSON 문자열 안에서는 `\t`로 이스케이프되지만, RFC 8259 §2는 탭을 토큰 사이의 정당한 공백으로 허용한다).

#### 제어문자가 유일하게 안전한 이유

[RFC 8259](https://www.rfc-editor.org/rfc/rfc8259.txt) §2 — 문법상 허용되는 공백:

```
ws = *( %x20 / %x09 / %x0A / %x0D )   ; Space, Tab, LF, CR
```

§7 — 문자열 안:

> "All Unicode characters may be placed within the quotation marks, except for the characters that MUST be escaped: quotation mark, reverse solidus, and the control characters (U+0000 through U+001F)."
> `unescaped = %x20-21 / %x23-5B / %x5D-10FFFF`

→ **적합한 JSON 텍스트에는 원시 바이트 0x01·0x02가 어느 위치에도 나타날 수 없다.** 문자열 밖은 위 네 가지 공백만, 문자열 안은 0x20 이상만 허용된다.

생산자 쪽도 확인했다. Spring Boot 4.1의 `JsonValueWriter.writeString()`은 ISO 제어문자를 전부 `\uXXXX`로 이스케이프한다
([소스](https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/json/JsonValueWriter.java)):

```java
case '"'  -> this.out.append("\\\"");
case '\\' -> this.out.append("\\\\");
case '\b' -> this.out.append("\\b");
case '\f' -> this.out.append("\\f");
case '\n' -> this.out.append("\\n");
case '\r' -> this.out.append("\\r");
case '\t' -> this.out.append("\\t");
default -> {
    if (Character.isISOControl(ch)) {
        this.out.append("\\u");
        this.out.append(String.format("%04X", (int) ch));
    }
    ...
}
```

→ **한 로그 이벤트는 반드시 한 줄이고**, 그 줄 안에 0x01·0x02는 존재할 수 없다. 스택트레이스도 `\n`으로 접힌 채 한 줄에 들어간다. 이 두 사실이 이 적재 방식 전체의 근거다.

QUOTE와 DELIMITER는 서로 달라야 하므로 0x01/0x02로 나눠 준다. 결과적으로 COPY는 **모든 줄을 인용되지 않은 단일 필드**로 읽고, 그 텍스트가 그대로 `jsonb`로 캐스팅된다.

#### 남는 함정 하나 — `\.`

PG 18에서 서버 쪽은 CSV의 `\.`를 EOF로 취급하지 않게 바뀌었지만, **psql은 여전히 그렇게 취급한다.**
<https://www.postgresql.org/docs/release/18.0/> 원문:

> "Prevent `COPY FROM` from treating `\.` as an end-of-file marker when reading CSV files (Daniel Vérité, Tom Lane)"
> "**psql will still treat `\.` as an end-of-file marker when reading CSV files from `STDIN`.** … This release also enforces that `\.` must appear alone on a line."

psql 소스에서도 확인된다 — `handleCopyIn()`이 `\.\n` 3바이트 또는 `\.\r\n` 4바이트와 **정확히 일치**하는 줄에서만 종료한다
([copy.c](https://github.com/postgres/postgres/blob/REL_18_STABLE/src/bin/psql/copy.c)).
JSON 한 줄은 `{`로 시작하므로 절대 걸리지 않고, 스크립트의 `sed -n '/^{/p'`가 한 겹 더 막는다.

### A-2. 컨테이너 문제 — 파일이 postgres 컨테이너 밖에 있다

**질문한 패턴은 동작한다. 다만 `STDIN`이 아니라 `PSTDIN`을 써야 확실하다.**

```bash
cat /var/log/app/backend.log \
  | docker exec -i promptstudio-postgres \
      psql -U "$POSTGRES_USER" -d promptstudio_logs -v ON_ERROR_STOP=1 \
      -c "\copy log_stage(raw) FROM PSTDIN WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x01', ON_ERROR ignore, ENCODING 'UTF8')"
```

세 가지가 맞물려야 한다.

**(1) `docker exec -i` — 있어야 하고, `-t`는 있으면 안 된다.**
Docker 공식 문서 원문 (<https://docs.docker.com/reference/cli/docker/container/exec/>, <https://docs.docker.com/reference/cli/docker/container/run/>):

> `-i, --interactive`: "Keep STDIN open even if not attached"
> `--tty` (`-t`): "attaches a pseudo-TTY to the container, connecting your terminal to the I/O streams of the container"
> "**Using the `-i` flag on its own allows for composition, such as piping input to containers.**"

즉 파이프에는 `-i` 단독이 문서가 명시하는 용법이다. `-it`을 쓰면 의사 터미널이 붙어 스트림에 라인 규율 처리가 끼어든다 — 조사용 셸을 열 때만 `-it`을 쓰고, **적재 파이프에는 절대 쓰지 않는다.**

**(2) `PSTDIN` — `-c`와 조합할 때 중요하다.**
<https://www.postgresql.org/docs/18/app-psql.html> 원문:

> "For `\copy ... from stdin`, data rows are read from **the same source that issued the command**, continuing until a line containing only `\.` is read or the stream reaches EOF."
> "**To read/write psql's standard input or output regardless of the current command source or `\o` option, write `from pstdin` or `to pstdout`.**"

`-c`로 실행하면 "명령을 낸 소스"는 명령 문자열이다. `STDIN`이 그 상황에서 무엇을 가리키는지는 psql 내부 상태(`pset.cur_cmd_source`)에 달려 있는데, `PSTDIN`은 **정의상 psql의 표준 입력**이므로 그 질문 자체가 사라진다. psql 소스에서도 `pstdin`은 `copystream = stdin`으로 직결된다. → **`-c`로 넘길 때는 `PSTDIN`을 쓴다.**

반대로 SQL 스크립트를 `psql <<'SQL'`로 stdin에 밀어 넣는 경우에는 **stdin이 이미 스크립트에 점유되어 있으므로** 데이터 파이프를 겸할 수 없다. 그래서 3단계 스크립트에서 `\copy`만 `-c`로 분리했다.

**(3) `\copy`는 옵션 문자열을 손대지 않는다 — `E'\x01'`이 서버까지 그대로 간다.**
psql `copy.c`의 `parse_slash_copy()`는 파일명 뒤의 나머지를 통째로 문자열로 잡고(`result->after_tofrom = pg_strdup(token)`), `do_copy()`가 그걸 검증·재인용 없이 그대로 이어 붙인다:

```c
printfPQExpBuffer(&query, "COPY ");
appendPQExpBufferStr(&query, options->before_tofrom);
appendPQExpBufferStr(&query, " FROM STDIN ");
appendPQExpBufferStr(&query, options->after_tofrom);
```

따라서 `E'\x01'`은 **서버의 SQL 파서가** 이스케이프 문자열 상수로 해석한다. psql이 중간에서 망가뜨리지 않는다.
문서도 같은 말을 한다: "Unlike most other meta-commands, the entire remainder of the line is always taken to be the arguments of `\copy`, and neither variable interpolation nor backquote expansion are performed in the arguments."

#### 회전된 gz

```bash
zcat /var/log/app/backend.log.2026-08-01.*.gz \
  | grep '^{' \
  | docker exec -i promptstudio-postgres psql -U "$POSTGRES_USER" -d promptstudio_logs \
      -c "\copy log_stage(raw) FROM PSTDIN WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x01', ON_ERROR ignore, ENCODING 'UTF8')"
```

현재 파일과 gz를 **한 번의 COPY로** 합치는 편이 낫다(파이프 한 줄로 이어 붙인다 — 3단계 스크립트가 그렇게 한다). COPY를 여러 번 나눠 부르면 그만큼 트랜잭션이 늘 뿐 이득이 없다.

#### 로그가 백엔드 컨테이너 안에만 있을 때

```bash
docker exec promptstudio-backend cat /var/log/app/backend.log \
  | grep '^{' \
  | docker exec -i promptstudio-postgres psql -U "$POSTGRES_USER" -d promptstudio_logs \
      -c "\copy log_stage(raw) FROM PSTDIN WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x01', ON_ERROR ignore, ENCODING 'UTF8')"
```

앞쪽 `docker exec`에는 `-i`가 **필요 없다**(stdin을 읽지 않는다). 뒤쪽에만 붙인다.
다만 이건 임시방편이고, 애초에 호스트 볼륨으로 빼는 게 맞다 — 컨테이너를 재생성하면 로그가 통째로 사라지기 때문이다.

### A-3. 서버사이드 `COPY`와 `pg_read_file()` — 쓰지 않는다

**서버사이드 `COPY ... FROM '/path'`**
<https://www.postgresql.org/docs/18/sql-copy.html> 원문:

> "Files named in a `COPY` command are read or written directly by the server, not by the client application. Therefore, they must reside on or be accessible to the database server machine, not the client."
> "`COPY` naming a file or command is only allowed to database superusers or users who are granted one of the roles `pg_read_server_files`, `pg_write_server_files`, or `pg_execute_server_program`, since it allows reading or writing any file or running a program that the server has privileges to access."

즉 **로그 파일을 postgres 컨테이너 안에서 보이게 만들어야 한다.** `docker-compose.yml`에 볼륨을 하나 더 붙인다는 뜻이고, 그건:

- 서비스 DB 컨테이너에 로그 디렉터리를 상시 마운트해 두는 것 — 필요 없는 결합이다
- 마운트를 바꾸려면 **컨테이너를 재생성**해야 한다(서비스 중단)
- 게다가 컨테이너 안 postgres 유저(uid 999)가 그 파일을 읽을 수 있어야 한다

`\copy` 파이프는 이 셋을 전부 피한다. 대가는 데이터가 클라이언트 연결을 통과한다는 것뿐이고("These operations are not as efficient as the SQL `COPY` command … because all data must pass through the client/server connection"), 하루 100k줄(≈50MB)에서는 무의미한 차이다.

**`pg_read_file()`** — 더 나쁘다.
<https://www.postgresql.org/docs/18/functions-admin.html> 원문:

> "Only files within the database cluster directory and the `log_directory` can be accessed, unless the user is a superuser or is granted the role `pg_read_server_files`."
> "This function is restricted to superusers by default, but other users can be granted EXECUTE to run the function."
> "The bytes read from the file are interpreted as a string in the database's encoding; an error is thrown if they are not valid in that encoding."

서버 파일 가시성 문제는 그대로 있으면서, 결과가 **하나의 거대한 `text` 값**이라 `string_to_table(..., E'\n')` 같은 걸로 다시 쪼개야 한다. 파일 전체가 한 값이므로 1GB 제한과 메모리도 걸린다. **쓰지 않는다.**

### A-4. PG 17·18이 더한 것 — `ON_ERROR`는 실제로 도움이 된다

릴리스 노트로 직접 확인했다.

**PostgreSQL 17** (<https://www.postgresql.org/docs/release/17.0/>):

> "Add new `COPY` option `ON_ERROR ignore` to discard error rows … The default behavior is `ON_ERROR stop`."
> "Add new `COPY` option `LOG_VERBOSITY` which reports `COPY FROM` ignored error rows"
> "Allow `COPY FROM` to report the number of skipped rows during processing … `pg_stat_progress_copy`.`tuples_skipped`"

**PostgreSQL 18** (<https://www.postgresql.org/docs/release/18.0/>):

> "Add `REJECT_LIMIT` to control the number of invalid rows `COPY FROM` can ignore … This is available when `ON_ERROR = 'ignore'`."
> "Add `COPY` `LOG_VERBOSITY` level `silent` to suppress log output of ignored rows"
> "Prevent `COPY FROM` from treating `\.` as an end-of-file marker when reading CSV files" (§A-1의 그 항목)

**JSON을 아는 COPY 포맷은 없다.** `FORMAT`은 여전히 `text` / `csv` / `binary` 셋뿐이다 — 18 문서의 옵션 목록에 JSON이 없다.

#### `ON_ERROR ignore`가 잡는 것과 못 잡는 것

문서 원문: "Specifies how to behave when encountering an error **converting a column's input value into its data type**."

- **잡는다**: JSON이 아닌 줄. `text → jsonb` 변환 실패이므로 정확히 해당한다. 로그 파일에 JVM 크래시 출력이나 `System.err` 찌꺼기가 섞여도 그 줄만 버리고 계속 간다. **이게 이 용도에 딱 맞는 이유다** — 한 줄이 깨졌다고 5만 줄 적재가 통째로 실패하면 안 된다.
- **못 잡는다**: `NOT NULL` 위반. 타입 변환 오류가 아니다. **빈 줄**은 CSV에서 필드 하나짜리 NULL이 되므로, `log_stage.raw`에 `NOT NULL`을 걸면 `ON_ERROR ignore`로도 막을 수 없다. → DDL에서 `log_stage.raw`를 nullable로 두고, 스크립트에서 `sed -n '/^{/p'`로 미리 떨구고, `INSERT … WHERE raw IS NOT NULL`로 한 번 더 거른다.

기본 `LOG_VERBOSITY`(=`default`)면 끝날 때 몇 줄이 버려졌는지 요약이 나온다. 어떤 줄이 버려졌는지 봐야 할 때만 `LOG_VERBOSITY verbose`를 붙인다(줄마다 NOTICE가 나오므로 평소엔 쓰지 않는다).

### A-5. 적재 없이 질의하기 — `file_fdw`는 여기선 값이 없다

DuckDB의 `read_json_auto`에 가장 가까운 것은 `file_fdw`다. PG 18에서 오히려 좋아졌다 — 릴리스 노트: "Add `on_error` and `log_verbosity` options to file_fdw", "Add `reject_limit` …".

```sql
CREATE EXTENSION file_fdw;
CREATE SERVER logsrv FOREIGN DATA WRAPPER file_fdw;
CREATE FOREIGN TABLE log_file (raw jsonb)
  SERVER logsrv
  OPTIONS (filename '/var/log/app/backend.log', format 'csv',
           delimiter E'\x02', quote E'\x01', on_error 'ignore');
```

**그런데 요구사항이 세 가지다** (<https://www.postgresql.org/docs/18/file-fdw.html>):

1. `CREATE EXTENSION file_fdw` — 확장 설치. `postgres:18.4` 이미지에 contrib이 포함돼 있는지 확인이 필요하다(→ [미확인](#미확인-목록)).
2. 권한 — 원문: "Changing table-level options requires being a superuser or having the privileges of the role `pg_read_server_files` (to use a filename) or the role `pg_execute_server_program` (to use a program), for security reasons: only certain users should be able to control which file is read or which program is run."
3. **파일이 서버 컨테이너 안에 보여야 한다** — 원문: "Relative paths are relative to the data directory." §A-3와 똑같은 볼륨 문제다.

게다가 실무적으로 결정적인 단점이 있다: **인덱스가 없다.** 질의할 때마다 파일 전체를 다시 파싱한다. 조사 한 번에 질의를 열 번쯤 던지는 게 정상인데, 그때마다 50MB를 CSV 파싱 + jsonb 파싱한다. 적재는 **한 번**이면 되고 그 뒤론 인덱스가 붙는다.

**결론: `file_fdw`는 볼륨 마운트라는 비용을 치르고 인덱스를 잃는다. 여기서는 적재가 낫다.**
(다만 만약 이미 로그 볼륨을 postgres 컨테이너에 물릴 계획이라면, `file_fdw` 외부 테이블을 **스테이징 대용**으로 쓰는 건 깔끔하다: `INSERT INTO app_log SELECT … FROM log_file;` 한 줄로 §3단계의 파이프가 사라진다.)

---

## B. 테이블 모양과 질의

### B-1. 생성 컬럼은 답이 아니다 — PG 18에서 두 번 막힌다

질문의 "`raw jsonb` + 생성 컬럼" 안은 PG 18에서 **두 개의 독립된 벽**에 부딪힌다.

#### 벽 1 — PG 18은 생성 컬럼의 기본이 VIRTUAL이고, VIRTUAL은 인덱스를 못 만든다

<https://www.postgresql.org/docs/release/18.0/>:

> "Allow generated columns to be virtual, and **make them the default** (Peter Eisentraut, Jian He, Richard Guo, Dean Rasheed). Virtual generated columns generate their values when the columns are read, not written. The write behavior can still be specified via the `STORED` option."

<https://www.postgresql.org/docs/18/ddl-generated-columns.html>: "A generated column is by default of the virtual kind."
`CREATE TABLE` 문서: "**`VIRTUAL` is the default.**"

그리고 이 기능을 넣은 커밋(`83ea6c54025bea67bcd4949a6d58d3fc11c3e21b`, Peter Eisentraut, 2025-02-07)의 메시지가 미지원 항목을 직접 나열한다
([pgsql-committers](https://www.postgresql.org/message-id/E1tgK6H-005ooT-PA@gemulon.postgresql.org)):

> "Some functionality that is currently not supported, but could possibly be added as incremental features, some easier than others:
> - **index on or using a virtual column**
> - hence also no unique constraints on virtual columns
> - extended statistics on virtual columns
> - foreign-key constraints on virtual columns
> - **not-null constraints on virtual columns** (check constraints are supported)
> - ALTER TABLE / DROP EXPRESSION
> - virtual column cannot have domain type
> - virtual columns are not supported in logical replication"

→ **`GENERATED ALWAYS AS (...)` 라고만 쓰면 PG 18에서는 인덱스를 못 붙인다.** 3.x 시절 감각으로 쓰면 조용히 VIRTUAL이 된다. 인덱스를 원하면 `STORED`를 **명시**해야 한다.
(문서의 제약 목록에는 인덱스 얘기가 없다. 커밋 메시지가 1차 자료다.)

#### 벽 2 — 타임스탬프 캐스팅은 IMMUTABLE이 아니라 STORED로도 못 만든다

`STORED`를 명시해도 이건 안 된다:

```sql
ts timestamptz GENERATED ALWAYS AS ((raw ->> '@timestamp')::timestamptz) STORED   -- ✗
```

생성 컬럼 제약: "The generation expression can only use **immutable** functions."
그런데 `text → timestamptz` 입력 함수는 `TimeZone`·`DateStyle` GUC에 의존하므로 IMMUTABLE일 수 없다.
<https://www.postgresql.org/docs/18/xfunc-volatility.html> 원문:

> "A common error is to label a function `IMMUTABLE` when its results depend on a configuration parameter. For example, a function that manipulates timestamps might well have results that depend on the `TimeZone` setting. For safety, such functions should be labeled `STABLE` instead."

PostgreSQL 메일링 리스트에도 같은 사안이 반복해서 올라온다 ([Tom Lane, "Creating an index on a timestamp with time zone cast to a date"](https://www.postgresql.org/message-id/1012707.1621880687@sss.pgh.pa.us), ["generated column cast from timestamptz to timestamp not OK"](https://www.postgresql.org/message-id/CAJA4AWRedm=hZAve7gU9OyPKM_9Jx30i7Mi7n5-Sc5bu9JX1OQ@mail.gmail.com)).
같은 이유로 **`(raw->>'@timestamp')::timestamptz` 위의 표현식 인덱스도 만들 수 없다.**

#### 그래서 — 적재 시점에 실제 컬럼으로 뽑는다

`log_stage`에 원문만 받고 `INSERT … SELECT`로 옮기면 두 벽이 동시에 사라진다:

- `INSERT`는 그냥 문장이다. 불변성 제약이 없다. 타임스탬프 캐스팅도 정규식 추출도 자유롭다
- 결과가 평범한 컬럼이라 인덱스·통계·`NOT NULL`이 전부 정상 동작한다
- `route`/`status`가 JSON 필드로 나오든 `message` 안의 `key=value`로 나오든 `COALESCE`로 한 자리에서 흡수된다 — **로그 포맷이 바뀌어도 적재 SQL만 고치면 되고 테이블은 그대로다**
- 스테이징은 옮긴 뒤 `TRUNCATE`해서 디스크를 바로 돌려준다

`STORED` 생성 컬럼이 나쁜 선택은 아니지만(예: `level`, `status`처럼 캐스팅이 불변인 것), 어차피 타임스탬프 때문에 `INSERT … SELECT`가 필요하다. 한 가지 방법으로 통일하는 편이 읽기 쉽다.

> 참고: `UNLOGGED`를 쓰는 이유는 WAL을 안 쓰기 때문이다 — 원문: "Data written to unlogged tables is not written to the write-ahead log … which makes them considerably faster than ordinary tables. However, they are not crash-safe: an unlogged table is automatically truncated after a crash or unclean shutdown."
> **파생 데이터에는 정확히 맞는 성질이다.** 크래시로 날아가면 다시 적재하면 된다. 그 대신 서비스 DB의 WAL·아카이브·복제에 로그 적재가 전혀 얹히지 않는다.

### B-2. 최종 DDL

```sql
CREATE UNLOGGED TABLE log_stage (
    raw jsonb                       -- NOT NULL 금지 (§A-4)
);

CREATE UNLOGGED TABLE app_log (
    ts          timestamptz NOT NULL,
    level       text        NOT NULL,
    request_id  text,
    route       text,
    status      int,
    duration_ms int,
    raw         jsonb       NOT NULL
);
```

**승격하는 필드를 6개로 제한한 것이 핵심이다.** `message`·`logger`·`thread`·`stack_trace`는 승격하지 않는다 — **거르거나 정렬하거나 묶는 대상이 아니고 결과를 볼 때만 읽기 때문**이다. `raw ->> 'message'`로 꺼내면 되고, 그 대신 힙 크기가 눈에 띄게 줄어든다(§C-4).

### B-3. 인덱스 — GIN은 필요 없다

```sql
CREATE INDEX app_log_ts_idx        ON app_log (ts);                                  -- 시간 범위
CREATE INDEX app_log_level_ts_idx  ON app_log (level, ts);                           -- 레벨 + 시간
CREATE INDEX app_log_reqid_idx     ON app_log (request_id text_pattern_ops);         -- requestId 접두어
CREATE INDEX app_log_route_ts_idx  ON app_log (route, ts)        WHERE status >= 400; -- 실패 집계
CREATE INDEX app_log_status_ts_idx ON app_log (status, ts)       WHERE status IS NOT NULL;
CREATE INDEX app_log_duration_idx  ON app_log (duration_ms DESC) WHERE duration_ms IS NOT NULL;
```

**`text_pattern_ops`가 요점이다.** DB 로케일이 `C`가 아니면(공식 이미지 기본은 `en_US.utf8`) 기본 opclass 인덱스는 `LIKE '3f9a%'`에 쓰이지 않는다.
<https://www.postgresql.org/docs/18/indexes-opclass.html> 원문:

> "The operator classes `text_pattern_ops`, `varchar_pattern_ops`, and `bpchar_pattern_ops` support B-tree indexes … The difference from the default operator classes is that the values are compared strictly character by character rather than according to the locale-specific collation rules. **This makes these operator classes suitable for use by queries involving pattern matching expressions (`LIKE` or POSIX regular expressions) when the database does not use the standard "C" locale.** … If you do use the C locale, you do not need the `xxx_pattern_ops` operator classes."
>
> "Note that you should also create an index with the default operator class if you want queries involving ordinary `<`, `<=`, `>`, or `>=` comparisons to use an index. Such queries cannot use the `xxx_pattern_ops` operator classes. (**Ordinary equality comparisons can use these operator classes, however.**)"

→ `request_id`는 접두어 검색과 등치 검색만 하므로 `text_pattern_ops` **하나면 충분하다.** 범위 비교를 안 하니 기본 opclass 인덱스를 따로 만들 필요가 없다.

**부분 인덱스(`WHERE status >= 400`)를 쓰는 이유:** 로그의 대부분은 `status`가 없는 줄(일반 애플리케이션 로그)이고, 조사할 때 보는 건 4xx/5xx다. 인덱스가 훨씬 작아진다.

#### GIN을 권하지 않는 이유

<https://www.postgresql.org/docs/18/datatype-json.html>:

> "`jsonb_path_ops` … A `jsonb_path_ops` index is usually much smaller than a `jsonb_ops` index over the same data … The technical difference between a `jsonb_ops` and a `jsonb_path_ops` GIN index is that the former creates independent index items for **each key and value** in the data, while the latter creates index items only for **each value**."

로그는 **키가 고정되고 값이 거의 전부 유일하다**(requestId, 타임스탬프, message). GIN이 잘하는 일 — "이 키를 가진 문서 찾기", "이 값을 포함하는 문서 찾기" — 는 로그 조사에서 거의 쓸 일이 없다. 반대로 값이 유일하면 GIN 항목 수가 행 수 × 키 수만큼 늘어나 인덱스가 힙만큼, 혹은 그 이상 커진다(§C-4).

또 하나 문서가 짚는 함정 — GIN 인덱스는 **인덱스 컬럼에 직접 적용된 연산자**만 쓴다:

> "the index could not be used for queries like the following, because though the operator `?` is indexable, it is not applied directly to the indexed column `jdoc`"

`raw ->> 'level' = 'ERROR'` 같은 우리가 실제로 쓰는 형태는 애초에 GIN 대상이 아니다. **필요한 필드는 이미 실제 컬럼으로 승격했으므로 GIN을 만들 이유가 남지 않는다.**

> 규모 감각을 같이 적어 둔다. 하루치 10만 행 · 힙 70MB 정도에서는 **인덱스 없이 순차 스캔해도 대개 1초 안쪽**이다. 위 인덱스들은 "있으면 좋은" 수준이고, 조사 편의(`ORDER BY ts` 즉시 응답)를 위한 것이지 없으면 못 쓰는 게 아니다. 7일치를 한꺼번에 올릴 때부터 차이가 난다.

### B-4. 실제로 던지게 되는 질의

**(1) requestId 접두어로 요청 하나의 전 생애**

사용자가 화면에서 본 id는 보통 앞 8자리다.

```sql
SELECT ts,
       level,
       raw -> 'log' ->> 'logger' AS logger,
       raw ->> 'message'         AS message,
       raw -> 'error' ->> 'type' AS error_type
FROM app_log
WHERE request_id LIKE '3f9a1c2e%'
ORDER BY ts;
```

**(2) 어느 창의 5xx를 엔드포인트별로**

```sql
SELECT route,
       count(*)                      AS failures,
       count(DISTINCT request_id)    AS requests,
       min(ts)                       AS first_seen,
       max(ts)                       AS last_seen
FROM app_log
WHERE status >= 500
  AND ts >= timestamptz '2026-08-01 14:00+09'
  AND ts <  timestamptz '2026-08-01 16:00+09'
GROUP BY route
ORDER BY failures DESC;
```

**(3) 엔드포인트별 지연 분포**

LLM 호출이 붙은 엔드포인트는 평균이 아니라 꼬리를 봐야 한다.

```sql
SELECT route,
       count(*)                                                    AS n,
       percentile_disc(0.50) WITHIN GROUP (ORDER BY duration_ms)   AS p50,
       percentile_disc(0.95) WITHIN GROUP (ORDER BY duration_ms)   AS p95,
       max(duration_ms)                                            AS worst,
       count(*) FILTER (WHERE status >= 500)                       AS failed
FROM app_log
WHERE duration_ms IS NOT NULL
  AND ts >= now() - interval '24 hours'
GROUP BY route
ORDER BY p95 DESC NULLS LAST;
```

**(4) 어템프트 하나에 얽힌 모든 로그**

MDC에 `attemptId`가 들어가면(관측성 계획 슬라이스 5) 이렇게 되고:

```sql
SELECT ts, level, raw -> 'log' ->> 'logger' AS logger, raw ->> 'message' AS message
FROM app_log
WHERE raw ->> 'attemptId' = '42'
ORDER BY ts;
```

아직 아니라면 경로로 찾은 뒤 requestId로 넓힌다:

```sql
WITH hits AS (
    SELECT DISTINCT request_id
    FROM app_log
    WHERE raw ->> 'message' LIKE '%/api/attempts/42/%'
)
SELECT l.ts, l.level, l.raw ->> 'message' AS message
FROM app_log l
JOIN hits USING (request_id)
ORDER BY l.ts;
```

**(5) "이 요청이 들어오기는 했나"**

```sql
SELECT count(*)                            AS lines,
       min(ts)                             AS first_line,
       max(ts)                             AS last_line,
       max(status)                         AS status,
       max(duration_ms)                    AS duration_ms
FROM app_log
WHERE request_id LIKE '3f9a1c2e%';
```

> ⚠️ **`lines = 0`을 "안 들어왔다"로 읽으면 안 된다.** 현재 `RequestLogFilter`는 **status ≥ 400일 때만** 한 줄을 남긴다(`backend/src/main/java/com/promptstudio/global/logging/RequestLogFilter.java:54-66`). 정상 2xx 요청은 핸들러가 따로 로그를 찍지 않는 한 **로그에 한 줄도 남지 않는다.**
> 즉 지금 구조에서 이 질의로 확실히 알 수 있는 건 "**실패한 채로** 들어왔는가"뿐이다. "들어오기는 했나"를 진짜로 답하려면 모든 요청에 한 줄(예: INFO 접근 로그)이 필요하다. 이건 로그 스키마 문제이지 SQL 문제가 아니다.

**(6) 예외 종류별 집계 + 대표 스택트레이스 한 개**

```sql
SELECT raw -> 'error' ->> 'type'                        AS error_type,
       count(*)                                         AS n,
       min(ts)                                          AS first_seen,
       (array_agg(request_id ORDER BY ts))[1]           AS sample_request,
       left((array_agg(raw -> 'error' ->> 'stack_trace' ORDER BY ts))[1], 1200) AS sample_stack
FROM app_log
WHERE level = 'ERROR'
  AND raw -> 'error' ->> 'type' IS NOT NULL
  AND ts >= now() - interval '7 days'
GROUP BY 1
ORDER BY n DESC;
```

---

## C. 안전하고 깔끔하게 유지하기

### C-1. 격리 — 같은 인스턴스의 **별도 데이터베이스**

세 후보를 실제 실패 모드 기준으로 비교한다.

| | 별도 스키마 | **별도 데이터베이스** | 별도 컨테이너 |
|---|---|---|---|
| `pg_dump` 오염 | **매번 `-N logs`를 기억해야 한다** | **자동으로 빠진다** (pg_dump는 DB 단위) | 빠진다 |
| 실수로 지우기 | `DROP SCHEMA` 오타 위험이 실데이터 옆에 있다 | `DROP DATABASE promptstudio_logs` — 원문이 파일이라 항상 안전 | 안전 |
| 디스크 blast radius | 같은 PGDATA 볼륨 | **같은 PGDATA 볼륨** | **분리됨** |
| autovacuum | 공유 | 공유 | 분리 |
| 앱 테이블과 조인 | 된다 | **안 된다** (dblink/fdw 필요) | 안 된다 |
| 운영 비용 | 0 | 0 (`CREATE DATABASE` 한 번) | 컨테이너·볼륨·포트·RAM |

**권장: 별도 데이터베이스 `promptstudio_logs`.**

근거를 순서대로:

1. **`pg_dump`가 데이터베이스 단위다.** 이게 결정적이다. 스키마로 나누면 `pg_dump promptstudio`가 로그 스키마를 **기본으로 포함한다.** 막으려면 매 백업마다 `-N logs`를 붙여야 하는데, 프롬프트에도 적혀 있듯 팀은 잊는다. 그리고 이 실패는 **조용하다** — 백업이 서서히 부풀고 복구가 느려질 뿐 아무도 알아채지 못한다. 별도 DB는 잊을 여지 자체가 없다.
2. **복구 동작이 한 줄이다.** 파일이 원본이므로 `DROP DATABASE promptstudio_logs`는 언제나 되돌릴 수 있는 조치다. "이상하면 통째로 지우고 다시 적재한다"가 성립하는 것 자체가 이 설계의 안전장치다.
3. **autovacuum 부담은 애초에 거의 없다.** 별도 DB여도 같은 인스턴스라 autovacuum 워커를 공유하지만, 이 테이블은 `INSERT` 뒤 `TRUNCATE`만 한다. `UPDATE`/`DELETE`가 없으니 dead tuple이 쌓이지 않고, `TRUNCATE`는 파일을 새로 만들기 때문에 vacuum 대상 자체가 사라진다. `UNLOGGED`라 WAL도 안 쓴다.
4. **별도 컨테이너는 과하다.** 별도 컨테이너만이 막아 주는 위험은 **하나**다 — 로그가 PGDATA 볼륨을 채워 서비스 DB까지 멈추는 것. 실재하는 위험이지만, EC2 한 대에 Postgres를 두 벌 띄우는 RAM·운영 비용 대신 **적재량을 스크립트가 강제하는 것**(§C-2)으로 훨씬 싸게 막을 수 있다. 주 몇 회 쓰는 조사 도구에 상시 프로세스를 하나 더 붙이는 건 비용이 편익을 넘는다.
5. **잃는 것은 앱 테이블과의 조인 하나다.** 정직하게 적어 둔다. `attempt` 테이블과 로그를 한 질의로 묶을 수 없다. 실제로는 조사 흐름이 "로그에서 attemptId 찾기 → 앱 DB에서 그 어템프트 보기"라 `\c`로 옮겨 두 번 질의하면 되고, 꼭 필요하면 작은 결과셋을 `\copy`로 옮기면 된다. 이걸 상시로 하고 싶어질 만큼 자주 필요하다면 그때 스키마 방식으로 바꾸면 된다 — 테이블 DDL은 그대로 쓸 수 있다.

### C-2. 보존 — **적재 스크립트가 매번 `TRUNCATE`한다**

요구는 "팀이 청소를 잊어도 조용히 무한히 자라지 않을 것"이다.

**권장: 적재-전-TRUNCATE.** `scripts/load-logs.sh`의 첫 SQL이 `TRUNCATE log_stage, app_log`다.

이게 최소 노력으로 요구를 만족시키는 이유:

- **청소를 잊는다는 개념이 없다.** 청소가 별도 작업이 아니라 **적재 동작 안에 들어 있다.** 크론도, 스케줄러도, 기억할 것도 없다.
- 테이블 크기의 상한이 **"마지막에 한 번 적재한 양"**으로 고정된다. 기본 `DAYS=1`이면 하루치(≈70MB)를 넘을 수 없다. 7일치를 보고 싶으면 `./scripts/load-logs.sh 7`로 그때만 올린다.
- **§C-3(중복 적재)이 공짜로 같이 해결된다.**
- 어차피 **원본 보존은 이미 Logback이 하고 있다** — `max-history`(기본 7)와 `total-size-cap`. DB에 보존 정책을 하나 더 만들 이유가 없다. **파일이 보존 계층이고 DB는 작업대다.**

#### 일 단위 파티셔닝은 권하지 않는다

`ts`로 RANGE 파티셔닝하고 오래된 파티션을 `DROP TABLE`하는 방식은 정석이고, 문서도 그 이점을 명확히 말한다 (<https://www.postgresql.org/docs/18/ddl-partitioning.html>):

> "Bulk loads and deletes can be accomplished by adding or removing partitions … Dropping an individual partition using `DROP TABLE`, or doing `ALTER TABLE DETACH PARTITION`, is **far faster** than a bulk operation. These commands also entirely avoid the `VACUUM` overhead caused by a bulk `DELETE`."

**그런데 여기서는 손해다:**

1. **파티션을 만들고 지우는 주체가 필요하다** — 크론이든 `pg_partman`이든. 그걸 잊는 게 정확히 이 절이 막으려던 실패다. TRUNCATE는 기계가 필요 없다.
2. **`UNLOGGED`를 포기해야 한다.** PG 18 `CREATE TABLE` 문서 원문: "**This form is not supported for partitioned tables.**" (릴리스 노트: "Disallow unlogged partitioned tables"). 부모를 UNLOGGED로 만들 수 없다.
3. **얻는 게 없다.** 파티션 프루닝이 의미 있으려면 데이터가 여러 날 쌓여 있어야 하는데, 여기 있는 건 방금 조사하려고 올린 하루치다.

**파티셔닝은 "로그를 상시 DB에 유지하기로 방침이 바뀌었을 때"의 다음 단계로 남겨 둔다.** 그때 필요한 형태만 적어 둔다:

```sql
CREATE TABLE app_log (...) PARTITION BY RANGE (ts);
CREATE TABLE app_log_20260801 PARTITION OF app_log
    FOR VALUES FROM ('2026-08-01') TO ('2026-08-02');
CREATE INDEX ON app_log (ts);        -- 부모에 만들면 자식과 이후 파티션에 자동 전파된다
DROP TABLE app_log_20260725;         -- 보존 만료
```

> 부모 인덱스 전파는 문서로 확인됨: "This automatically creates a matching index on each partition, and any partitions you create or attach later will also have such an index."
> `DROP TABLE`은 부모에 `ACCESS EXCLUSIVE` 락을 잡는다. 조사용이라 무관하지만 알아 둘 것.

#### 그래도 남는 디스크 위험 한 가지

`./scripts/load-logs.sh 30`처럼 크게 부르면 한 번에 2GB가 들어올 수 있고, PGDATA 볼륨은 서비스 DB와 공유다. 스크립트 맨 앞에 한 줄이면 막힌다:

```bash
avail=$(df -Pk /var/lib/docker | awk 'NR==2{print $4}')
(( avail > 5*1024*1024 )) || { echo "디스크 여유 5GB 미만 — 적재 중단"; exit 1; }
```

### C-3. 같은 파일을 두 번 적재해도 중복이 없게

**권장: `TRUNCATE`로 구조적으로 해결한다.** 적재는 "덮어쓰기"이지 "추가"가 아니다. 두 번 돌려도 결과가 같고(멱등), 실패해서 중간에 끊겨도 다시 돌리면 그만이다. 자연키도, 유니크 인덱스도, 배치 테이블도 필요 없다 — **없는 게 제일 관리하기 쉽다.**

굳이 **누적**이 필요해지면(예: 이미 지워진 오래된 gz를 계속 갖고 있고 싶을 때) 그때 아래를 붙인다:

```sql
CREATE TABLE load_batch (
    source_file text        NOT NULL,
    file_size   bigint      NOT NULL,
    file_mtime  timestamptz NOT NULL,
    loaded_at   timestamptz NOT NULL DEFAULT now(),
    rows_loaded bigint      NOT NULL,
    PRIMARY KEY (source_file, file_size, file_mtime)
);
```

`(경로, 크기, mtime)`이 자연키로 성립하는 근거는 **회전된 `.gz`는 불변**이라는 사실이다. 적재 전에 이 키가 있으면 건너뛴다.

> ⚠️ **현재 쓰이고 있는 `backend.log`에는 이 방식을 쓰면 안 된다.** 계속 자라므로 크기·mtime이 매번 다르고, 이전 적재분과 새 적재분이 겹친다. 살아 있는 파일은 언제나 **TRUNCATE 후 통째로 다시** 넣는다.

### C-4. 용량 추정

**가정** (전부 명시한다. 실측이 아니다):

- 하루 **100,000줄**
- ECS JSON 한 줄 평균 **450바이트** (`@timestamp` 30 + `log`(level·FQN logger) 90 + `process`(pid·thread) 60 + `service` 50 + `requestId` 50 + `message` 120 + `ecs` 25). 스택트레이스가 붙는 ERROR 줄은 훨씬 크지만 드물다
- `jsonb`는 키/값마다 4바이트 JEntry가 붙으므로 압축 텍스트 대비 **약 +15~20%** → 줄당 **≈530바이트**
- **압축은 일어나지 않는다.** <https://www.postgresql.org/docs/18/storage-toast.html> 원문: "The TOAST management code is triggered only when a row value to be stored in a table is wider than `TOAST_TUPLE_THRESHOLD` bytes (**normally 2 kB**)." 530바이트 행은 문턱을 못 넘어 **인라인 비압축**으로 저장된다. (`default_toast_compression` 기본값은 `pglz`지만 여기선 발동하지 않는다.)
- 힙 튜플 오버헤드 ≈ 36바이트(23B 헤더 + null 비트맵 + 정렬 + 4B 라인 포인터)

| 구성 | 줄당 | 10만 행(1일) | 70만 행(7일) |
|---|---|---|---|
| `raw jsonb`만 | ≈ 570 B | **≈ 57 MB** | ≈ 400 MB |
| + 승격 컬럼 6개 (ts·level·request_id·route·status·duration_ms ≈ 100 B) | ≈ 670 B | **≈ 67 MB** | ≈ 470 MB |
| + 위 6개 btree 인덱스 | +≈ 27 B/행×6 | **+≈ 27 MB** | +≈ 190 MB |
| **합계 (권장 구성)** | | **≈ 95 MB** | **≈ 660 MB** |
| 참고: `message`·`logger`·`thread`까지 승격했다면 | ≈ 870 B | ≈ 87 MB | ≈ 610 MB |
| 참고: `GIN (raw)` 추가 시 | | **+50~120 MB** | +350~840 MB |

읽을 점 두 가지:

- **필드를 6개로 제한한 게 ~20% 절약**이다. `message`·`logger`·`thread`까지 승격하면 힙이 67→87MB로 뛴다. 거르지도 정렬하지도 묶지도 않는 필드는 `raw`에 두는 게 맞다.
- **GIN 하나가 나머지 전부보다 크다.** 로그는 값이 거의 유일해서 GIN 항목 수가 폭발한다. §B-3의 결론이 숫자로도 나온다.
- 스테이징까지 합치면 적재 **중** 한때 힙의 약 두 배를 쓴다. 그래서 `INSERT` 직후 `TRUNCATE log_stage`를 넣었다.

실제 값은 적재 후 한 줄로 확인한다:

```sql
SELECT pg_size_pretty(pg_total_relation_size('app_log'))  AS total,
       pg_size_pretty(pg_relation_size('app_log'))        AS heap,
       pg_size_pretty(pg_indexes_size('app_log'))         AS indexes,
       count(*)                                           AS rows,
       pg_size_pretty(avg(pg_column_size(raw))::bigint)   AS avg_raw
FROM app_log;
```

---

## D. 앱이 런타임에 DB로 직접 쓰는 안 — **반대한다**

팀의 원래 직관은 "요청마다 비동기로 로그를 DB에 INSERT"였다. 이 설정에서 어떤 일이 벌어지는지 항목별로 본다.

### 1. DB가 죽으면 — 결정적

**가장 조사하고 싶은 순간에 로그가 없다.** DB 장애·커넥션 고갈·디스크 풀·마이그레이션 실패는 이 팀이 실제로 겪을 장애의 큰 부분인데, 그 순간의 로그가 정확히 그 DB에 들어가려다 사라진다. 파일 로깅은 DB와 무관하게 남고, 나중에 DB가 살아난 뒤 그 파일을 적재해서 **장애 시점을 소급 조사할 수 있다.** 파일→적재 설계의 존재 이유가 이것이다.

### 2. 커넥션 고갈 — 장애를 악화시킨다

HikariCP 풀이 마르면 로그 INSERT도 커넥션을 기다린다. 로그가 **커넥션을 소비하는 경쟁자**가 되어, 로그를 남기려다 실제 요청을 더 막는다. 부하가 몰리는 순간 로그량도 같이 늘어나므로 **정확히 최악의 시점에 최악으로 동작한다.** 전용 풀을 따로 두면 완화되지만 그만큼 커넥션 예산을 떼 줘야 한다.

### 3. 기동 시점 로깅 — 구멍이 남는다

`DataSource` 빈이 만들어지기 전의 로그(설정 오류, 프로퍼티 누락, JDBC URL 오타, Flyway 실패)는 DB에 넣을 수 없다. 그런데 **배포 직후 장애에서 제일 보고 싶은 게 그 로그다.** 결국 "기동 로그는 파일, 그 뒤는 DB"라는 이중 경로가 생기고, 조사할 때 두 군데를 봐야 한다.

### 4. 요청 경로 지연

비동기 어펜더를 쓰면 요청 스레드는 큐에 넣고 빠지므로 지연 자체는 피할 수 있다. 문제는 **큐가 찼을 때**다. 어느 구현이든 선택지는 둘뿐이다 — **버리거나 막거나.** 버리면 장애 시점의 로그가 없고(부하와 로그량이 같이 늘어나므로 하필 그때 버려진다), 막으면 요청 지연이 로그 시스템에 묶인다. 파일 어펜더도 같은 구조지만 **로컬 디스크 쓰기는 네트워크 왕복 + 트랜잭션보다 두 자릿수 빠르므로** 큐가 찰 확률 자체가 다르다.

### 5. 트랜잭션 상호작용 — 가장 고약하다

로그 INSERT가 요청 트랜잭션에 참여하면 **롤백될 때 로그도 같이 사라진다.** 즉 **실패한 요청의 로그가 정확히 실패했다는 이유로 없어진다.** 완전히 거꾸로다.
피하려면 `REQUIRES_NEW`나 별도 커넥션이 필요하고, 그러면 요청마다 커넥션을 두 개 쓴다(→ 2번을 악화). 여기에 "로그 INSERT가 실패하면 요청은 어떻게 되나"라는 결정이 새로 생기고, 그걸 잘못 다루면 **로깅이 요청을 실패시킨다.**

### 6. 보존 부담 — 이미 해결된 문제를 다시 만든다

파일 로깅은 Logback이 회전·gzip·`max-history`·`total-size-cap`을 **설정 몇 줄로** 해 준다. DB에 직접 쓰면 그 전부를 새로 만들어야 한다 — 파티셔닝 또는 삭제 크론, 그리고 그걸 잊었을 때의 디스크 풀. 그 디스크는 **서비스 DB와 같은 볼륨**이다. 로그 보존을 잊으면 서비스가 멈춘다.

### 7. 코드 복잡도

| | 파일 → 적재 | 앱이 직접 INSERT |
|---|---|---|
| 애플리케이션 코드 | **0줄** (`application.yml` 설정만) | 어펜더 또는 리스너, 스키마, 마이그레이션, 실패 처리, 재시도, 백프레셔 |
| 실패 모드 | 적재 스크립트가 실패한다 (아무 영향 없음) | 요청 경로에 새 실패 모드가 생긴다 |
| 롤백 | 스크립트를 안 돌리면 끝 | 마이그레이션 되돌리기 + 재배포 |
| 포맷 변경 | 적재 SQL만 고친다. 과거 파일도 새 SQL로 다시 읽힌다 | 스키마 마이그레이션 |

### 결론

**파일이 원본이고 PostgreSQL은 질의 표면이다. 앱은 DB에 로그를 쓰지 않는다.**

핵심은 1번과 5번이다. 나머지는 완화할 수 있지만, **"DB가 죽은 순간의 로그가 없다"와 "롤백된 요청의 로그가 사라진다"는 완화가 아니라 설계 자체의 성질**이다. 그리고 로그를 보는 이유가 정확히 그 두 상황이다.

> 이건 "DB에 아무것도 쓰지 말라"는 말이 아니다. 이 레포는 이미 `AttemptLlmCall`·`LlmUsageTracker`로 LLM 호출의 토큰·지연·상태를 DB에 적재하고 있고, **그건 옳다.** 구분선은 이렇다:
>
> - **서비스 기능이 그 값에 의존하면** → 도메인 데이터다. DB에 트랜잭션으로 쓴다 (`AttemptLlmCall`이 이쪽).
> - **사람이 사후에 읽기만 하면** → 로그다. 파일에 쓰고 필요할 때 적재한다.
>
> `requestId`를 두 세계에 다 남기면(관측성 계획의 슬라이스 4) 조사할 때 둘을 손으로 이을 수 있다. 그게 지금 빠져 있는 연결고리다 — `docs/observability-plan.md`도 같은 지점을 짚고 있다("로그와 DB를 잇는 구멍 — `requestId` 컬럼이 없다").

---

## 미확인 목록

실행해서 확인하지 못한 것들이다. **이 작업트리에는 `psql`이 없고 띄워진 Postgres 컨테이너도 없다.** 문서·소스로 근거는 확보했으나 실측하지 않았음을 분명히 한다. 각 항목에 확인용 명령을 붙였다.

1. **`DELIMITER E'\x02'` / `QUOTE E'\x01'`을 COPY가 실제로 받는지** — 문서는 "single one-byte character"만 요구하고 제어문자를 배제하지 않으며, 알려진 명시적 금지는 개행/CR뿐이다. 그러나 **직접 실행해 확인하지 못했다.** → **미확인**
   ```bash
   printf '{"a":1,"b":"x,y\\"z\\tw"}\n{"a":2}\n' | docker exec -i promptstudio-postgres \
     psql -U "$POSTGRES_USER" -d postgres -v ON_ERROR_STOP=1 \
     -c 'CREATE TEMP TABLE t(raw jsonb)' \
     -c "\copy t(raw) FROM PSTDIN WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x01')" \
     -c 'SELECT count(*), raw->>'"'"'b'"'"' FROM t GROUP BY 2'
   ```
2. **`-c "\copy ... FROM PSTDIN"`이 파이프를 실제로 읽는지** — psql 문서가 `pstdin`을 "psql의 표준 입력, 현재 명령 소스와 무관"이라고 정의하고 소스에서 `copystream = stdin`으로 직결되는 것까지 확인했으나, `docker exec -i` 파이프와 조합해 **돌려보지 못했다.** 위 1번 명령이 이것도 같이 검증한다. → **미확인**
3. **`-c`에서 `STDIN`과 `PSTDIN`이 같은지** — `-c` 실행 시 `pset.cur_cmd_source`가 무엇인지 소스에서 최종 확인하지 못했다. 그래서 정의상 안전한 `PSTDIN`을 권장했다. `STDIN`이 실패한다면 `PSTDIN`으로 바꾸면 되고, 그 반대는 성립하지 않는다. → **미확인**
4. **`docker exec -it`(TTY)이 파이프 데이터를 실제로 손상시키는지** — Docker 문서는 `-i` 단독이 파이프용이라고 명시하지만("Using the `-i` flag on its own allows for composition, such as piping input to containers"), `-t`를 함께 썼을 때의 손상은 **문서로 확인하지 못했다.** 권장안은 `-t`를 쓰지 않으므로 실무상 무해하다. → **미확인**
5. **`@timestamp`의 정확한 문자열 형식** — `ElasticCommonSchemaStructuredLogFormatter`가 `members.add("@timestamp", ILoggingEvent::getInstant)`로 **커스텀 포맷터 없이** 쓴다는 것까지는 소스로 확인했다. 따라서 `Instant.toString()` = UTC + `Z` 접미어 + 가변 소수 자릿수(0/3/6/9)일 것으로 **추론**했으나 실제 출력을 보지 못했다. 오프셋이 붙어 있으므로 `::timestamptz`는 세션 TimeZone에 무관하지만, **가변 자릿수 때문에 텍스트 정렬은 시간순과 다르다**(`...05Z` > `...05.123Z`). 이것이 `ts`를 실제 `timestamptz` 컬럼으로 두어야 하는 또 다른 이유다. → **확인 한 줄**: `head -1 /var/log/app/backend.log | jq -r '."@timestamp"'`
6. **`postgres:18.4` 이미지에 `file_fdw`(contrib)가 들어 있는지** — 공식 이미지의 contrib 포함 여부를 1차 자료로 확인하지 못했다. §A-5의 결론(적재가 낫다)은 이것과 무관하다. → **확인 한 줄**: `docker exec promptstudio-postgres ls /usr/share/postgresql/18/extension/ | grep file_fdw`
7. **DB 인코딩과 로케일** — `text_pattern_ops` 권장은 로케일이 `C`가 아니라는 전제에 서 있다(공식 이미지 기본은 `en_US.utf8`로 알려져 있으나 확인하지 못했다). `C` 로케일이면 문서 원문대로 "you do not need the `xxx_pattern_ops` operator classes". → **확인 한 줄**: `docker exec promptstudio-postgres psql -U "$POSTGRES_USER" -d postgres -c 'SHOW server_encoding' -c 'SHOW lc_collate'`
8. **VIRTUAL 생성 컬럼이 표현식 인덱스와 매칭되는지** — 가상 컬럼은 뷰처럼 재작성 단계에서 원래 식으로 펼쳐지므로, `((raw->>'level'))` 위의 표현식 인덱스가 가상 컬럼 질의에 쓰일 **가능성**은 있다. 그러나 1차 자료에 서술이 없고 확인하지 못했다. 권장안은 실제 컬럼을 쓰므로 해당 없다. → **미확인**
9. **`ON_ERROR ignore`가 `jsonb` 파싱 실패를 실제로 건너뛰는지** — 문서상 "converting a column's input value into its data type"에 해당하므로 맞을 것이나 실행 확인은 못 했다. 스크립트의 `sed -n '/^{/p'`가 독립적인 2차 방어선이므로, 이게 틀려도 정상 동작한다. → **확인 한 줄**: 위 1번에 `{"broken` 같은 줄을 섞어 본다
10. **Logback 비동기 어펜더의 큐 포화 시 기본 동작** — §D-4에서 "버리거나 막는다"고만 적고 구체적 기본값(임계치·버려지는 레벨)은 쓰지 않았다. Logback 1차 자료로 확인하지 않았기 때문이다. 논지(둘 중 무엇이든 장애 시점에 나쁘다)는 기본값과 무관하다. → **미확인**
11. **§C-4의 용량 수치** — 전부 **계산이며 실측이 아니다.** 줄 평균 450바이트, jsonb +15~20%, 튜플 오버헤드 36바이트라는 가정에서 나왔다. 실제 로그 한 줄의 길이가 다르면 비례해서 달라진다. §C-4 끝의 `pg_total_relation_size` 질의로 적재 후 바로 실측할 수 있다. → **추정치**

---

## 참고 링크

**PostgreSQL 18 문서**
- COPY (옵션 전체 / 권한 / ON_ERROR / REJECT_LIMIT): <https://www.postgresql.org/docs/18/sql-copy.html>
- psql (`\copy` / `pstdin` / 파싱 규칙): <https://www.postgresql.org/docs/18/app-psql.html>
- 생성 컬럼 (VIRTUAL 기본 / 제약 목록): <https://www.postgresql.org/docs/18/ddl-generated-columns.html>
- CREATE TABLE (`VIRTUAL is the default` / UNLOGGED): <https://www.postgresql.org/docs/18/sql-createtable.html>
- 함수 변동성 (IMMUTABLE / STABLE / TimeZone 의존): <https://www.postgresql.org/docs/18/xfunc-volatility.html>
- 연산자 클래스 (`text_pattern_ops`): <https://www.postgresql.org/docs/18/indexes-opclass.html>
- JSON 타입 (jsonb 인덱싱 / `jsonb_path_ops`): <https://www.postgresql.org/docs/18/datatype-json.html>
- 선언적 파티셔닝: <https://www.postgresql.org/docs/18/ddl-partitioning.html>
- TOAST (2kB 문턱): <https://www.postgresql.org/docs/18/storage-toast.html>
- 서버 파일 접근 함수 (`pg_read_file`): <https://www.postgresql.org/docs/18/functions-admin.html>
- file_fdw: <https://www.postgresql.org/docs/18/file-fdw.html>
- `default_toast_compression`: <https://www.postgresql.org/docs/18/runtime-config-client.html>

**릴리스 노트**
- PostgreSQL 17: <https://www.postgresql.org/docs/release/17.0/>
- PostgreSQL 18: <https://www.postgresql.org/docs/release/18.0/>

**소스 · 커밋**
- 가상 생성 컬럼 커밋 `83ea6c5` (미지원 항목 목록): <https://www.postgresql.org/message-id/E1tgK6H-005ooT-PA@gemulon.postgresql.org>
- psql `copy.c` (옵션 그대로 전달 / `\.` 처리): <https://github.com/postgres/postgres/blob/REL_18_STABLE/src/bin/psql/copy.c>
- Spring Boot `JsonValueWriter` (제어문자 이스케이프): <https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/json/JsonValueWriter.java>
- Spring Boot ECS 포맷터 (JSON 모양): <https://github.com/spring-projects/spring-boot/blob/v4.1.0/core/spring-boot/src/main/java/org/springframework/boot/logging/logback/ElasticCommonSchemaStructuredLogFormatter.java>

**그 외**
- RFC 8259 (JSON — 제어문자 / 공백 ABNF): <https://www.rfc-editor.org/rfc/rfc8259.txt>
- `docker exec`: <https://docs.docker.com/reference/cli/docker/container/exec/>
- `docker run` (`-i` 단독 = 파이프): <https://docs.docker.com/reference/cli/docker/container/run/>

**이 레포**
- `docs/spring-boot-4.1-observability.md` — 구조화 로깅·파일 회전 설정의 근거
- `docs/observability-plan.md` — 로그 필드(`requestId`·`route`·`durationMs`) 계획, DuckDB 대안 검토
- `backend/src/main/java/com/promptstudio/global/logging/RequestLogFilter.java` — 현재 요청 로그가 status ≥ 400에서만 나오는 지점
