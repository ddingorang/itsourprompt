# Backend

모두의 프롬프트의 API 서버입니다. 프롬프트를 받아 LLM을 호출하고, 워크스페이스를 저장하고, 채점을 요청하고, 제출된 세션의 피드백을 만듭니다. 서비스 전체 설명은 [루트 README](../README.md)에 있습니다.

## 모듈 지도

패키지는 계층이 아니라 **도메인**으로 나눕니다. `com.promptstudio` 아래 열세 개입니다.

| 모듈 | 맡는 것 |
|---|---|
| `attempt` | 풀이와 턴. 프롬프트를 받아 코드 생성을 지시하고 결과를 저장하는 중심 모듈 |
| `problem` | 문제와 저장소 동기화 |
| `relay` | 릴레이 게임의 방·순서·진행 단계, WebSocket 채널 |
| `ai` | OpenAI 어댑터. 코드 생성, 프롬프트 게이트, 피드백 생성 프롬프트가 모두 여기 |
| `ranking` | 문제별 비용 순위 |
| `me` | 내 정보와 내 풀이 목록 |
| `user` · `auth` · `guest` | 계정, 로그인, 비로그인 세션과 소유권 이전 |
| `pricing` | 모델 단가표. 설정 파일의 값을 부팅 때 DB로 옮긴다 |
| `rabbit` | 채점 큐 어댑터. 요청 발행과 결과 수신 |
| `gitlab` | 문제 저장소 어댑터 |
| `global` | 보안 설정, 예외 변환, 로깅 |

규모가 고르지 않습니다. `attempt` 80개, `relay` 65개 파일이고 나머지는 대부분 열 개 안팎입니다. 앞의 둘이 이 서비스의 본체입니다.

## 계층

각 도메인 모듈 안은 같은 모양으로 나뉩니다.

```
attempt/
├── controller/     HTTP 경계. 요청·응답 DTO를 여기서 소유한다
├── service/        업무 규칙
├── domain/         도메인 타입
├── repository/     저장소 구현
├── port/           바깥으로 나가는 호출의 인터페이스
└── exception/      이 도메인의 예외
```

**계층마다 자기 DTO를 소유합니다.** service가 controller의 응답 타입을 참조하지 않습니다 — 그렇게 두면 화면 사정으로 업무 규칙이 흔들립니다.

## 포트와 어댑터

바깥으로 나가는 호출은 **업무 모듈이 인터페이스를 소유하고, 어댑터 모듈이 구현합니다.** 의존 방향이 어댑터 → 업무 쪽으로 흐릅니다.

| 포트 (소유) | 구현 (어댑터) | 바깥 |
|---|---|---|
| `attempt/port/CodeGenerator` | `ai/OpenAiCodeGenerator` | OpenAI |
| `attempt/port/PromptScopeValidator` | `ai/OpenAiPromptScopeValidator` | OpenAI |
| `attempt/port/FeedbackGenerator` | `ai/FeedbackGenerators` | OpenAI |
| `attempt/port/CodeRunPublisher` | `rabbit/` | RabbitMQ |
| `problem/port/ProblemSourceClient` | `gitlab/GitLabProblemSourceClient` | GitLab |

`repository`는 포트로 두지 않습니다. 저장소는 갈아끼울 대상이 아니라 이 애플리케이션의 일부라고 봤습니다.

## 영속성

**스키마의 진실은 `src/main/resources/schema.sql` 하나입니다.** `ddl-auto: none`이라 엔티티 애노테이션이 테이블을 만들지 않습니다. 컬럼을 바꾸려면 이 파일을 고칩니다. 개발 DB가 이미 떠 있는 경우를 위해 파일 끝에 `ALTER TABLE ... IF NOT EXISTS` 마이그레이션을 덧붙이는 방식입니다.

JPA와 jOOQ를 함께 씁니다. 엔티티 하나를 저장·수정하는 자리는 JPA, 여러 테이블·여러 행을 한 번에 다뤄야 하는 자리는 jOOQ입니다. jOOQ 저장소는 조회만 하지 않습니다 — 멱등키 선점이나 만료 세션 정리처럼 한 문장으로 끝내야 하는 쓰기도 맡습니다.

## 테스트

테스트 62개입니다. DB가 필요한 테스트는 Testcontainers로 PostgreSQL을 띄웁니다.

```bash
./gradlew test
```

## API 문서

- 서버를 띄우면 `/swagger-ui.html`에 OpenAPI 문서가 뜹니다.
- 전체 엔드포인트와 인증·소유권 규칙, 에러 코드표는 [`docs/api.md`](../docs/api.md)에 있습니다.
- 설계 문서는 [`backend/docs/`](docs/) 아래에 있습니다. 일부는 결정이 아니라 조사 기록이니 각 문서 머리의 작성일과 상태를 먼저 보세요.
