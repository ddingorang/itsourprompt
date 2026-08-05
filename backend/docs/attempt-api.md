# 문제 풀이 API

## AI 코드 생성 요청

```http
POST /api/attempts/{attemptId}/turns
Content-Type: application/json
Idempotency-Key: <optional UUID>
```

동일한 `attemptId`에서 AI 코드 생성은 한 번에 하나만 처리한다. 다른 브라우저나 탭에서 이미 AI 요청을 처리 중이면, 새 요청은 AI를 호출하거나 저장하지 않고 아래 응답을 반환한다.

```http
409 Conflict
Content-Type: application/json
```

```json
{
  "code": "code-generation-in-progress",
  "message": "다른 창에서 이 문제의 AI 요청을 처리하고 있습니다. 잠시 후 새로고침 후 다시 시도해 주시기 바랍니다."
}
```

프런트엔드는 이 응답을 받으면 사용자에게 안내한 뒤, 완료 후 문제 풀이를 다시 조회하거나 재시도할 수 있게 한다. 성공 응답과 요청 형식에는 변경이 없다.
