# Frontend

prompt.practice의 화면입니다. 서비스 전체 설명은 [루트 README](../README.md)에 있습니다.

## 구조

```
src/
├── app/        라우터와 앱 껍데기
├── pages/      라우트 하나당 파일 하나
├── features/   도메인별 컴포넌트와 로직
├── shared/     여러 기능이 함께 쓰는 것
└── mocks/      개발용 목 데이터
```

`pages/`는 라우트를 받아 배치만 하고, 실제 화면 조각은 `features/` 아래에 있습니다.

## 라우트

| 경로 | 화면 |
|---|---|
| `/` | 랜딩 |
| `/problems` | 문제 목록 |
| `/problems/:problemId` | 워크스페이스 — 문제를 새로 열 때 |
| `/attempts/:attemptId` | 워크스페이스 — 진행 중이거나 제출된 풀이를 열 때 |
| `/attempts/:attemptId/feedback` | 제출 피드백 |
| `/ranking` | 랭킹 |
| `/my` | 내 정보와 내 풀이 (로그인 필요) |
| `/relay`, `/relay/rooms/:roomId` | 릴레이 대기실과 게임 방 (로그인 필요) |
| `/login`, `/signup` | 인증 |

**워크스페이스 라우트가 둘인데 같은 컴포넌트를 렌더합니다.** 문제를 처음 열면 `/problems/:id`이고, 풀이가 만들어지면 `/attempts/:id`로 옮겨 갑니다. 풀이 상태를 화면이 들고 있지 않고 주소가 들고 있어서, 새로고침하거나 링크를 그대로 열어도 같은 자리로 돌아옵니다.

## features

| 디렉터리 | 담는 것 |
|---|---|
| `workspace` | 파일 탐색기와 코드 뷰어. 직전 턴과의 차이를 줄 단위로 표시 |
| `submission` | 프롬프트 입력 |
| `attempt` | 턴 기록, 토큰 사용량 |
| `feedback` | 피드백 렌더링, 채점 결과 목록 |
| `problem` | 문제 목록과 문제 설명 |
| `relay` | 릴레이 방, 게임 진행, 음성 |
| `ranking` · `me` · `auth` · `theme` | 랭킹표, 내 정보, 로그인 상태, 라이트/다크 |

## shared

`api/apiClient.ts` 하나가 모든 요청을 통과합니다. 인증은 HttpOnly 쿠키라 클라이언트가 토큰을 들고 있지 않고, 로그인 여부는 저장값이 아니라 `GET /api/me` 응답으로 판단합니다.

그 밖에 `components/`(버튼·헤더·푸터·로딩·빈 상태), `layout/`, `hooks/`, `types/`, `a11y/`가 있습니다.

## 개발 서버

```bash
npm install
npm run dev      # http://localhost:5173
npm run build    # tsc 타입체크 + 프로덕션 빌드
```

Vite 개발 서버가 `/api`와 `/ws`를 `localhost:9090`의 백엔드로 넘깁니다. 그래서 프론트만 띄울 때는 별도 환경변수가 필요 없습니다. 프록시 타임아웃은 10분으로 잡혀 있는데, 턴 추가와 제출이 LLM 호출 때문에 수 분까지 걸리기 때문입니다.
