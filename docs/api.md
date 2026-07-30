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
| POST | `/api/attempts` | 불필요 | 어템프트 |
| GET | `/api/attempts/{id}` | 불필요 | 어템프트 |
| POST | `/api/attempts/{id}/turns` | 불필요 | 어템프트 |
| POST | `/api/attempts/{id}/submit` | 불필요 | 어템프트 |
| GET | `/api/attempts/{id}/feedback` | 불필요 | 피드백 |
| POST | `/api/auth/signup` | 불필요 | 인증 |
| POST | `/api/auth/login` | 불필요 | 인증 |
| POST | `/api/auth/logout` | 불필요 (멱등) | 인증 |
| GET | `/api/me` | **필요** | 인증 |

## 인증 방식

**세션 + HttpOnly 쿠키**다. JWT 같은 토큰을 클라이언트가 저장하지 않는다.

1. `POST /api/auth/login` 성공 → 서버가 인증 정보를 HTTP 세션에 저장하고 `Set-Cookie: JSESSIONID=...; HttpOnly`를 내려준다.
2. 이후 브라우저가 쿠키를 자동 전송하므로 클라이언트는 별도 헤더를 붙이지 않는다.
3. 로그인 여부는 클라이언트 저장값이 아니라 `GET /api/me` 응답(200/401)으로 판단한다.

인증이 필요한 경로는 `/api/me/**` 하나뿐이고, 나머지는 전부 공개다. 즉 문제·어템프트 API는 비로그인으로도 호출된다. 인증이 필요한 경로에 세션 없이 접근하면 Spring Security 필터가 컨트롤러 진입 전에 401 `unauthenticated`로 차단한다.

CSRF는 비활성화되어 있어 상태 변경 요청에 CSRF 토큰이 필요하지 않다. 쿠키 전송은 same-origin 전제다(개발 환경은 Vite proxy `/api` → 9090).

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

예외: 요청 JSON 자체가 파싱 불가한 경우(깨진 JSON, 바디 누락)나 서버 내부 오류(500)는 위 핸들러를 거치지 않아 `{code, message}` 형식이 아닌 프레임워크 기본 응답이 나올 수 있다. 클라이언트는 `code`가 없는 4xx/5xx도 처리할 수 있어야 한다.

### 전체 에러 코드표

| HTTP | code | 언제 발생 |
|---|---|---|
| 400 | `invalid-request` | 요청 바디 검증(`@Valid`) 실패 — 필수 누락·형식·길이 위반 |
| 400 | `attempt-has-no-turns` | 턴이 하나도 없는 어템프트를 제출 |
| 401 | `bad-credentials` | 로그인 실패 (아이디 없음/비밀번호 불일치를 구분하지 않음) |
| 401 | `unauthenticated` | 세션 없이 인증 필요 경로 접근 (Security 필터가 차단) |
| 403 | `access-denied` | 인증됐으나 권한 부족 (현재 권한 등급이 하나라 사실상 미발생) |
| 404 | `problem-not-found` | 해당 ID의 문제가 없음 |
| 404 | `attempt-not-found` | 해당 ID의 어템프트가 없음 |
| 404 | `feedback-not-found` | 어템프트는 있으나 아직 제출 전이라 피드백이 없음 |
| 409 | `problem-inactive` | 비활성 문제로 새 어템프트를 시작하려 함 |
| 409 | `attempt-already-submitted` | 이미 제출된 어템프트에 턴을 추가하려 함 |
| 409 | `feedback-in-progress` | 해당 어템프트의 피드백 생성이 진행 중 |
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

랜딩 화면에서 고를 수 있는 문제의 ID와 제목만 반환한다. 비활성 문제는 목록에서 제외된다.

- **인증**: 불필요
- **파라미터**: 없음

**성공 응답 — 200**

| 필드 | 타입 | 설명 |
|---|---|---|
| `problems` | array | 문제 요약 목록 (비어 있을 수 있음) |
| `problems[].id` | number | 문제 ID |
| `problems[].title` | string | 문제 제목 |

```json
{
  "problems": [
    { "id": 1, "title": "Hello World 출력" },
    { "id": 2, "title": "SSAFY 출력" },
    { "id": 3, "title": "환영 메시지 출력" }
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
| `files` | array | 문제가 제공하는 스켈레톤 파일 목록 |
| `files[].path` | string | 파일 경로 |
| `files[].content` | string | 파일 내용 |

```json
{
  "id": 1,
  "title": "Hello World 출력",
  "specMd": "# Hello World 출력\n\n표준 출력으로 `Hello, World!`를 출력하세요.\n",
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
| `status` | string | `IN_PROGRESS` \| `SUBMITTED` |

이 응답에 피드백은 포함되지 않는다. 피드백은 `GET /api/attempts/{id}/feedback` 또는 제출 응답으로만 받는다.

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
      ]
    }
  ],
  "status": "IN_PROGRESS"
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
  "status": "IN_PROGRESS"
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

- **인증**: 불필요

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 어템프트 ID |

**성공 응답 — 200**: [AttemptResponse](#attemptresponse-공통-스키마)

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 404 | `attempt-not-found` | 해당 ID의 어템프트가 없음 |

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

**성공 응답 — 200**: [AttemptResponse](#attemptresponse-공통-스키마). `turns` 배열 끝에 이번 턴이 추가되고 `files`가 갱신된다.

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 400 | `invalid-request` | `prompt`가 없거나 공백만 |
| 404 | `attempt-not-found` | 해당 ID의 어템프트가 없음 |
| 409 | `duplicate-request` | 같은 `Idempotency-Key`의 요청이 처리 중 |
| 409 | `feedback-in-progress` | 이 어템프트의 피드백 생성이 진행 중 |
| 409 | `attempt-already-submitted` | 이미 제출된 어템프트 |
| 502 | `ai-provider-error` | AI 제공자 호출 실패 |
| 504 | `run-timeout` | AI 코드 생성 시간 초과 |

502/504로 실패한 경우 `Idempotency-Key` 기록은 삭제되므로 같은 키로 재시도할 수 있다.

### POST /api/attempts/{id}/submit

문제 명세와 어템프트의 전체 턴 기록을 바탕으로 턴별 프롬프트 피드백과 세션 전체 피드백을 생성해 저장하고 어템프트를 종료한다(`status` → `SUBMITTED`). 이미 제출된 어템프트를 다시 제출하면 AI를 재호출하지 않고 저장된 피드백을 그대로 반환한다. AI 호출이 포함되므로 응답이 오래 걸릴 수 있다(서버 측 피드백 생성 상한 5분, 초과 시 504 `feedback-timeout`).

- **인증**: 불필요
- **요청 바디**: 없음
- **`Idempotency-Key`**: 지원하지 않는다. 재제출 자체가 멱등이다.

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 어템프트 ID |

**성공 응답 — 200**: [FeedbackResponse](#feedbackresponse-계약)

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 400 | `attempt-has-no-turns` | 턴이 하나도 없는 어템프트 |
| 404 | `attempt-not-found` | 해당 ID의 어템프트가 없음 |
| 409 | `feedback-in-progress` | 같은 어템프트의 제출이 이미 진행 중 |
| 502 | `ai-provider-error` | AI 제공자 호출 실패 |
| 504 | `feedback-timeout` | 피드백 생성 시간 초과 |

---

# 피드백 (Feedback)

## FeedbackResponse 계약

`POST /api/attempts/{id}/submit`과 `GET /api/attempts/{id}/feedback`이 같은 형태를 반환한다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `turns` | array | 턴별 피드백 |
| `turns[].turn` | number | 턴 번호. **1부터 시작**한다 |
| `turns[].feedbackMd` | string | 해당 턴 프롬프트에 대한 피드백 (**Markdown**) |
| `overallMd` | string | 세션 전체에 대한 피드백 (**Markdown**) |

계약 사항:

- `turns[].turn`은 **1부터** 매겨진다. `AttemptResponse.turns` 배열의 인덱스 `n`은 `turn = n + 1`에 대응한다.
- `feedbackMd`와 `overallMd`는 **Markdown 문자열**이다. 클라이언트가 Markdown으로 렌더해야 한다.
- **턴별 피드백 도입 이전에 제출된 어템프트는 `turns`가 빈 배열(`[]`)이다.** 이때는 `overallMd`만 렌더한다. 즉 `turns.length === 0`인 경우를 정상 케이스로 처리해야 한다.
- 턴별 피드백이 있는 경우 `turns`의 길이는 어템프트의 턴 수와 같다(전부 배정되거나 하나도 배정되지 않는다).
- 이 봉투는 필드 추가 방식으로만 확장한다. **클라이언트는 모르는 JSON 키를 무시해야 한다.**

```json
{
  "turns": [
    {
      "turn": 1,
      "feedbackMd": "## 좋은 점\n출력 형식을 명확히 지정했습니다.\n\n## 개선점\n어떤 파일을 수정해야 하는지 함께 알려주면 더 정확한 결과를 얻습니다."
    },
    {
      "turn": 2,
      "feedbackMd": "## 개선점\n\"고쳐줘\"처럼 대상이 모호한 표현 대신 기대 동작을 서술하세요."
    }
  ],
  "overallMd": "## 전체 평가\n요구사항을 단계적으로 좁혀간 흐름이 좋았습니다.\n\n## 다음 세션 제안\n첫 프롬프트에 입출력 예시를 포함해 보세요."
}
```

구버전 어템프트(턴별 피드백 없음):

```json
{
  "turns": [],
  "overallMd": "## 전체 평가\n요구사항을 단계적으로 좁혀간 흐름이 좋았습니다."
}
```

### GET /api/attempts/{id}/feedback

제출 시 생성해 저장한 프롬프트 피드백을 턴별 피드백과 전체 피드백으로 반환한다. 제출 전에는 조회할 수 없다.

- **인증**: 불필요

| 경로 파라미터 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `id` | number | 필수 | 어템프트 ID |

**성공 응답 — 200**: [FeedbackResponse](#feedbackresponse-계약)

**에러**

| HTTP | code | 언제 |
|---|---|---|
| 404 | `attempt-not-found` | 해당 ID의 어템프트가 없음 |
| 404 | `feedback-not-found` | 어템프트가 아직 `IN_PROGRESS` — 제출 후 조회 가능 |

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
