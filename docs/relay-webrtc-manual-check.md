# 릴레이 WebRTC 수동 검증과 시그널링 규약

시그널링·mesh·DataChannel이 실제 브라우저에서 성립하는지 확인하는 절차.
검증은 실제 릴레이 화면(A505FE의 `/relay`)으로 한다.

## 준비

백엔드(9090)와 A505FE 프론트 개발 서버(5173)를 띄운다. coturn은 로컬에서 필요 없다 —
같은 호스트의 브라우저끼리는 host 후보로 직결된다.

## 절차

1. 창(프로필) 두 개에서 `http://localhost:5173/relay`를 열고 **서로 다른 계정으로** 로그인한다.
   같은 브라우저 프로필의 탭 두 개는 안 된다 — 세션 쿠키를 공유해서 같은 사용자가 되고,
   같은 사용자의 두 번째 소켓은 첫 소켓을 밀어낸다(설계 의도). 시크릿 창이나 다른 프로필을 쓴다.

2. A가 방을 만들고 방 번호를 B에게 알려 입장시킨다. 대기실에서 두 참가자의 좌석 카드에
   연결 상태 점이 초록(P2P connected)이 되는지 본다.

3. **타이핑 미리보기**: 게임 시작 후 주자가 프롬프트 칸에 타이핑하면 상대 화면의
   "입력 중" 카드에 실시간으로 나타난다. 이 데이터는 백엔드 로그에 없어야 정상이다 —
   DataChannel은 서버를 지나지 않는다.

4. **리액션**: 이모지 버튼을 누르면 상대 좌석 카드에 3초간 표시된다.

5. **음성**: 양쪽에서 "🎤 마이크 켜기"(권한 허용). 재협상이 일어나고 상대 소리가 들린다.
   하울링 주의 — 이어폰을 쓰거나 한쪽을 음소거.

6. **재접속**: B를 새로고침한다. 소켓이 스냅샷으로 복구되고 P2P가 다시 성립해야 한다.

## 배포 환경에서 다른 점

- coturn이 있어야 한다(`docker-compose.yml`). `TURN_EXTERNAL_IP`에 공인 IP,
  `RELAY_TURN_URLS`(예: `turn:i15a505.p.ssafy.io:3478`)와 자격증명을 채운다.
- nginx에 `/ws` 프록시 블록(Upgrade 헤더 + 긴 read timeout)이 필요하다.
- HTTPS면 소켓도 `wss://`가 된다(클라이언트가 페이지 프로토콜을 따라간다).
- 진짜 NAT 너머 검증은 한쪽을 모바일 테더링으로 붙여서 한다. `about:webrtc`(Firefox)나
  `chrome://webrtc-internals`에서 선택된 후보 쌍이 `relay`면 TURN을 탄 것이다.

## 자동 검증 기록

브라우저 없이도 프로토콜과 P2P 성립을 검증했다. 시그널링 프로토콜(중계·발신자 위조
무시·재접속 밀어내기·오프라인 대상 에러·24KB SDP)은 websockets 클라이언트로,
DataChannel 성립과 타이핑·리액션 교환은 aiortc(파이썬 WebRTC)로 아래 규약 그대로 확인했다.

## 시그널링 규약 (클라이언트 구현 기준)

A505FE의 `features/relay/useRelayRtc.ts`가 이 규약의 구현이다.

- 소켓: `/ws/relay/{roomId}` — 게임 상태 이벤트와 같은 채널이다.
- 접속 직후 `peer.list`(이미 접속 중인 userId들)를 받는다. **받은 쪽이 initiator** —
  목록의 전원에게 `RTCPeerConnection`과 DataChannel(`"relay"`)을 만들고 offer를 보낸다.
  기존 피어들은 `peer.joined`를 받고 offer를 기다린다. initiator가 명확해야 glare가 없다.
- offer 충돌(재협상 등)은 perfect negotiation으로 푼다: polite(userId가 큰 쪽)가 양보한다.
- 보내기: `{"type": "signal.offer|signal.answer|signal.ice", "targetUserId": N, "payload": {...}}`
- 받기: `{"type": 같음, "payload": {"fromUserId": N, "data": 보낸 payload}}` —
  `fromUserId`는 서버가 세션에서 확정한 값이다(클라이언트가 실은 값은 무시).
- 대상이 접속 중이 아니면 `signal.error`가 발신자에게 돌아온다. 그 피어의 `peer.joined`가
  다시 오면 재시도한다.
- ICE 설정: `GET /api/relay/ice-servers` 응답을 그대로 `RTCPeerConnection` 생성자에 넣는다.
- DataChannel 메시지 규약: `{"kind":"typing","text":...}`, `{"kind":"reaction","emoji":...}`.
  서버는 이 채널을 모른다 — 잃어도 되는 데이터만 싣는다(게임 상태는 항상 서버 채널).
