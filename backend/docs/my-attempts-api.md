# 마이페이지 제출 이력 API

## 목적

마이페이지의 `YOUR PROGRESS` 영역에서 현재 로그인 사용자가 제출 완료한 풀이를 모두 보여주고, 각 풀이의 피드백 화면으로 이동할 수 있게 한다.

같은 사용자가 같은 문제를 여러 번 제출했다면 각 제출은 별도 항목으로 반환한다.

## 요청

```http
GET /api/me/attempts
```

세션 쿠키(`JSESSIONID`) 기반 인증이 필요하다. 사용자 ID를 요청 경로나 요청 본문으로 전달하면 안 된다.

## 성공 응답

```http
200 OK
Content-Type: application/json
```

```json
[
  {
    "attemptId": 141,
    "problemId": 3,
    "problemTitle": "사용자 프로필 컴포넌트",
    "submittedAt": "2026-07-31T05:10:32Z"
  },
  {
    "attemptId": 118,
    "problemId": 3,
    "problemTitle": "사용자 프로필 컴포넌트",
    "submittedAt": "2026-07-29T09:20:00Z"
  }
]
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `attemptId` | number | 피드백 화면 이동에 사용할 어템프트 ID |
| `problemId` | number | 문제 상세 화면 식별자 |
| `problemTitle` | string | 문제 제목 |
| `submittedAt` | string \| null | 제출 완료 시각(ISO-8601) |

`submittedAt`은 이번 기능 이전에 저장된 기존 제출 기록에서는 `null`일 수 있다. 프런트는 `null`이면 날짜를 표시하지 않거나 `기록 없음`으로 표시한다.

## 정렬과 범위

- 현재 로그인 사용자의 기록만 반환한다.
- `SUBMITTED` 상태인 어템프트만 반환한다.
- 동일 문제의 중복 제출도 모두 반환한다.
- `submittedAt` 내림차순, 동일하거나 `null`이면 `attemptId` 내림차순으로 정렬한다.

## 오류 응답

| 상태 | 응답 예시 | 의미 |
| --- | --- | --- |
| `401` | `{ "code": "unauthenticated", "message": "로그인이 필요합니다." }` | 로그인 세션이 없음 또는 만료됨 |

## 프런트 연결 방법

목록 항목을 클릭하면 아래 경로로 이동한다.

```text
/feedback/{attemptId}
```

피드백 화면은 기존 소유권 검사를 다시 수행하므로, 다른 사용자의 `attemptId`를 URL에 입력해도 조회할 수 없다.
