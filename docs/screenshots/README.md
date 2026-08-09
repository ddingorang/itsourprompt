# 사이트 전체 화면 캡쳐

| 항목 | 값 |
| --- | --- |
| 대상 | <https://lets.promptpractice.run> (옛 주소 `i15a505.p.ssafy.io`는 이리로 301) |
| 캡쳐일 | 2026-08-10 |
| 기준 | `origin/FE-main` (`27472fc`) — 배포본 헤더가 새 서비스 이름을 달고 문제 목록에 모아보기 탭이 있어 확인했다 |
| 조건 | 뷰포트 1440×900, `deviceScaleFactor: 2`, `locale: ko-KR`, `fullPage` |
| 장수 | 다크 28장 + 라이트 7장 = 35장 |

라우트는 12개이고 아래 표가 그 전부다. 화면은 11종이며 `/problems/:problemId`와
`/attempts/:attemptId`는 같은 컴포넌트를 렌더한다.

다크가 기본 테마다. 테마는 `prefers-color-scheme`이 아니라 앱이 직접 들고 있는
`localStorage['prompt-practice-color-mode']` 값으로 정해진다.

## 비로그인으로 보이는 화면

| 파일 | 라우트 | 무엇이 담겼나 |
| --- | --- | --- |
| `01-landing.png` | `/` | 랜딩 5개 섹션 전체 (4237px) |
| `02-problems.png` | `/problems` | 문제 14개 중 1페이지(10개)와 모아보기 탭 |
| `03-workspace.png` | `/problems/2` | 작업장 — 아직 아무 턴도 없는 상태 |
| `04-workspace-game.png` | `/problems/6` | 게임 문제. 오른쪽 셋째 탭이 「채점」이 아니라 **「플레이」** |
| `05-workspace-python.png` | `/problems/10` | 파이썬 문제 |
| `06-attempt-promptlog.png` | `/attempts/267` | 제출까지 끝난 남의 풀이. **연 순간의 화면** |
| `07-attempt-problem.png` | `/attempts/267` | 같은 화면에서 「문제」 탭 |
| `08-attempt-grading.png` | `/attempts/267` | 같은 화면에서 「채점」 탭 — 5/5 통과 |
| `09-feedback.png` | `/attempts/267/feedback` | 프롬프트 총평 + 작업 패턴 총평(가져갈 한 줄) + 턴별 피드백 |
| `10-feedback-turn.png` | `/attempts/267/feedback` | 턴 2 탭 |
| `11-ranking.png` | `/ranking?problem=4` | 비용순 랭킹 3건 |
| `12-ranking-problems.png` | `/ranking?problem=4` | 「전체 14」로 문제 목록을 펼친 상태 |
| `13-login.png` | `/login` | |
| `14-signup.png` | `/signup` | |
| `15-error.png` | `/error` | 사유 없이 직접 열었을 때의 기본 문구 |
| `16-notfound.png` | 없는 경로 | **전용 404 화면이 없다.** react-router 기본 오류 화면이 그대로 나온다 |

`06`이 「프롬프트 기록」 탭인 것은 실수가 아니다. 턴이 있는 어템프트를 열면 화면이
스스로 그 탭을 연다(`ProblemDetailPage`, `loadedAttempt.turns.length` 분기). 기본값인
「문제」 탭을 보려면 `07`처럼 눌러야 한다.

## 로그인해야 보이는 화면

| 파일 | 라우트 | 무엇이 담겼나 |
| --- | --- | --- |
| `17-my.png` | `/my` | 진도 2/14, 제출 2건 |
| `18-ranking-mine.png` | `/ranking?problem=1` | 로그인 상태라 「나의 최고 기록」이 채워진 랭킹 |
| `19-relay-lobby.png` | `/relay` | 방 목록 + 방 만들기 폼(모달이 아니라 로비 아래에 늘 펼쳐져 있다) |
| `20-relay-create.png` | `/relay` | 방 이름을 넣어 버튼이 「방 만들기」로 바뀐 상태 |
| `21-relay-waiting-host.png` | `/relay/rooms/85` | 대기실 — 방장 시점(게임 시작 버튼) |
| `22-relay-waiting-guest.png` | `/relay/rooms/85` | 대기실 — 참가자 시점 |
| `23-relay-my-turn.png` | `/relay/rooms/85` | 진행 중, 내 차례 (프롬프트 입력 가능) |
| `24-relay-other-turn.png` | `/relay/rooms/85` | 진행 중, 남의 차례 |
| `25-relay-generating.png` | `/relay/rooms/85` | 주자가 낸 프롬프트로 AI가 코드를 만드는 동안의 대기자 화면 |
| `26-relay-my-turn-2.png` | `/relay/rooms/85` | 두 번째 주자 차례 |
| `27-relay-finished.png` | `/relay/rooms/85` | 종료 — 점수판·총평·턴별 피드백 |
| `28-relay-finished-turn.png` | `/relay/rooms/85` | 종료 화면에서 턴 2 탭 |

## 라이트 모드

최근 작업의 상당수가 라이트 모드 손질이라 대표 화면만 함께 남긴다.

| 파일 | 대응하는 다크 |
| --- | --- |
| `light-01-landing.png` | `01` |
| `light-02-problems.png` | `02` |
| `light-03-attempt.png` | `06` |
| `light-04-feedback.png` | `09` |
| `light-05-ranking.png` | `11` |
| `light-06-relay-finished.png` | `27` |
| `light-07-my.png` | `17` |

## 화면 속 데이터는 전부 실물이다

목(mock)을 켜지 않았고 응답을 가로채지도 않았다. 배포 서버가 실제로 내려준 값이다.

- **`06`~`11`의 어템프트 267번**은 다른 사람이 문제 4를 실제로 풀어 제출한 기록이다.
  제출까지 끝난 어템프트는 로그인 없이 누구나 열 수 있어 그대로 찍힌다. 프롬프트 3턴,
  줄 단위 diff, 5/5 통과한 채점 결과가 모두 그 사람의 것이다.
- **`17`·`18`의 제출 2건**과 **`19`~`28`의 릴레이 한 판**은 캡쳐용 계정
  `shotuser1`(테스터1)·`shotuser2`(테스터2)로 이번에 직접 만들었다. 릴레이는 방 #85에서
  문제 1(Hello World)을 1바퀴·2좌석으로 끝까지 진행했고, 두 턴 모두 실제 AI 호출이다.

계정 비밀번호는 문서와 스크립트에 넣지 않는다 — 아래처럼 환경변수로 넘긴다.

## 캡쳐하며 걸린 것

**랜딩은 뷰포트를 키우면 안 된다.** 섹션이 `min-h-screen`이라 뷰포트를 문서 높이로
늘리면 섹션도 같이 늘어나 4237px짜리 페이지가 21000px로 부푼다. 뷰포트는 900px로 두고
`fullPage`에 맡긴다. 대신 스크롤 등장 애니메이션이 있어 캡쳐 전에 페이지 끝까지 훑고
맨 위로 돌아온다.

**작업장은 `fullPage`만으로 부족하다.** 높이가 고정된 3분할 레이아웃이라 코드 뷰어와
문제 설명이 각각 내부 스크롤로 잘린다. 세로축(`height`/`max-height`/`overflow-y`)만
풀어야 하고, 가로나 flex 행의 폭을 건드리면 세 컬럼이 뷰포트를 나눠 갖지 못해 텍스트가
옆으로 잘린다. 이 처리는 작업장 계열(`03`~`08`, `light-03`)에만 걸었다 — 피드백 화면까지
풀면 코드 뷰어가 다 펼쳐져 4000px이 넘어가고, 실제로 보이는 화면과 딴판이 된다.

**어템프트를 열면 탭이 저절로 바뀐다.** 위에 적은 대로다. 「프롬프트 기록」 탭을
누르는 코드를 넣고도 결과 이미지가 한 픽셀도 안 바뀌길래 클릭이 실패한 줄 알았는데,
이미 그 탭이 열려 있었다.

**릴레이는 턴 제한시간 안에 프롬프트를 내야 한다.** 처음에는 기본값 2분짜리 방을
만들어 놓고 대기실·진행 화면을 찍는 사이에 두 턴이 모두 시간초과로 넘어가, 아무도
프롬프트를 내지 않은 채 게임이 끝났다. 점수판이 전부 `—`인 종료 화면이 나온다.
다시 찍을 때는 제한시간을 최대(300초)로 두고 만들었다.

**우측 탭은 `<button role="tab">`이다.** 명시 role이 암묵 role을 이기므로
`getByRole('button')`으로는 안 잡히고 `getByRole('tab')`을 써야 한다.

## 다시 찍으려면

세 스크립트가 이 디렉토리에 있다. **대상은 배포본으로 고정돼 있다** — 로컬 개발 서버를
찍으려면 각 스크립트 위쪽의 `BASE`를 바꿔야 한다.

준비물부터. 이 저장소에는 Playwright가 없으므로 아무 디렉토리에서나 받아서 거기서
스크립트를 돌린다.

```bash
npm install playwright
npx playwright install chromium
```

리눅스에서는 크로미움이 `libnspr4`·`libnss3`·`libasound2`를 요구하고, 한글 폰트
(Noto Sans CJK)가 없으면 글자가 전부 두부(□□□)로 찍힌다. 둘 다 없으면 각각 브라우저가
아예 안 뜨거나, 뜨는데 읽을 수 없는 이미지가 나온다.

```bash
# 1) 비로그인 화면 + 라이트 모드 (35장 중 21장)
node capture-site.mjs <출력디렉토리>
node capture-site.mjs <출력디렉토리> 01-landing 11-ranking   # 일부만 다시

# 2) 릴레이 한 판을 처음부터 끝까지 (방 생성 → 대기 → 진행 → 종료)
SITE_PW='...' node relay-full.mjs <출력디렉토리>

# 3) 로그인 상태 화면 (마이페이지·나의 최고 기록·라이트 릴레이)
SITE_PW='...' node capture-auth.mjs <출력디렉토리> <끝난_방_번호>
```

계정 이름(`shotuser1`·`shotuser2`)은 스크립트 안에 적혀 있고, 비밀번호만 `SITE_PW`로
받는다 — **둘의 비밀번호는 같아야 한다.** 계정이 없으면 `POST /api/auth/signup`으로
먼저 만든다(`username`/`password`/`nickname`/`email`).

`2`는 실제 AI 호출이 두 번 일어나고 몇 분 걸린다. 방을 만들 때 턴 제한시간을 최대
(300초)로 넣는 것은 스크립트가 알아서 한다 — 사람이 로비에서 고를 필요는 없다.

캡쳐가 끝나면 **이미지를 눈으로 열어 본다.** 한글이 두부(□□□)로 나오거나 패널이
잘린 것은 스크립트 출력만 봐서는 성공한 것처럼 보인다.
