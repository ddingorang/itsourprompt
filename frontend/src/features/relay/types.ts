/**
 * 릴레이 게임 도메인 타입. 백엔드 relay 패키지의 응답 DTO와 1:1 대응한다.
 *
 * 릴레이는 어템프트와 다른 상태 모델이다 — 방 하나가 서버 권위 상태(누구 차례인가,
 * 지금 어느 단계인가)를 들고 있고, 클라이언트는 그 스냅샷(RelayRoom)을 WebSocket으로
 * 통째로 받아 그대로 그린다. 클라이언트가 누적해 들고 있는 게임 상태는 없다 —
 * 재접속하면 스냅샷 한 번으로 복구된다.
 */

/**
 * 방의 진행 단계.
 *
 *   WAITING            입장을 받는 중. 방장이 시작하면 좌석이 확정된다
 *   PLAYING            현재 좌석의 주자가 프롬프트를 입력할 차례
 *   TURN_GENERATING    프롬프트를 받아 AI가 코드를 생성하는 중
 *   TURN_GRADING       생성된 코드의 빌드/테스트 채점 결과를 기다리는 중
 *   FEEDBACK_GENERATING 마지막 턴까지 끝나 피드백을 생성하는 중
 *   FINISHED           피드백까지 준비 완료
 */
export type RelayRoomStatus =
  | 'WAITING'
  | 'PLAYING'
  | 'TURN_GENERATING'
  | 'TURN_GRADING'
  | 'FEEDBACK_GENERATING'
  | 'FINISHED';

export interface RelayParticipant {
  userId: number;
  nickname: string;
  /** 풀이 순서(0-based). 게임 시작 시점에 입장 순서로 부여되며 시작 전에는 null. */
  seatOrder: number | null;
  joinedAt: string;
  /** 게임 중 이탈했는지. 이탈해도 좌석은 남는다. */
  left: boolean;
}

/** 로비 목록의 방 한 줄. 들어갈지 결정하는 데 필요한 것만 온다. */
export interface RelayRoomSummary {
  roomId: number;
  /** 방장이 붙인 방 이름. 이름 도입 전에 만들어진 방은 null. */
  name: string | null;
  problemId: number;
  problemTitle: string;
  hostUserId: number;
  hostNickname: string;
  participantCount: number;
  maxParticipants: number;
  totalLaps: number;
  /** 한 턴의 입력 제한시간(초). */
  turnTimeLimitSeconds: number;
  createdAt: string;
}

export interface RelayRoomListResponse {
  rooms: RelayRoomSummary[];
}

/** 방 상태 스냅샷. REST 조회와 WebSocket room.state 이벤트가 같은 모양을 준다. */
export interface RelayRoom {
  roomId: number;
  /** 방장이 붙인 방 이름. 이름 도입 전에 만들어진 방은 null. */
  name: string | null;
  problemId: number;
  hostUserId: number;
  status: RelayRoomStatus;
  totalLaps: number;
  maxParticipants: number;
  /** 한 턴의 입력 제한시간(초). 방장이 개설 시 정하지 않았으면 서버 기본값(120)이 채워져 있다. */
  turnTimeLimitSeconds: number;
  /** 시작 시점에 확정된 좌석 수. 시작 전에는 null. */
  seatCount: number | null;
  /** 릴레이 진행 인덱스(0-based). 좌석은 seatCount로 나눈 나머지, 바퀴는 몫. */
  currentTurnIndex: number;
  /** 지금 차례인 좌석. 릴레이 진행 중이 아니면 null. */
  currentSeat: number | null;
  currentLap: number | null;
  /** 총 턴 수 = 좌석 × 바퀴. 좌석 확정 전에는 null. */
  totalTurns: number | null;
  /** 시작 스켈레톤이 통과시킨 테스트 수 — 첫 주자 점수의 기준선. 결과 전에는 null. */
  baselinePassed: number | null;
  baselineTotal: number | null;
  turnDeadline: string | null;
  participants: RelayParticipant[];
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

/** 완료된 턴의 요약. 프롬프트 원문은 오지 않는다(공개 여부가 방 옵션으로 열려 있다). */
export interface RelayTurnSummary {
  turnIndex: number;
  seatOrder: number;
  lap: number;
  authorUserId: number;
  aiSummary: string;
  changedPaths: string[];
}

export interface RelayTurnResponse {
  turn: RelayTurnSummary;
  room: RelayRoom;
}

export interface RelayFile {
  path: string;
  content: string;
}

/** 방의 현재 코드. 지금까지의 턴을 모두 반영한 파일 전체. */
export interface RelayCode {
  appliedTurns: number;
  files: RelayFile[];
}

export type RelayRunStatus =
  | 'SUCCEEDED'
  | 'COMPILE_ERROR'
  | 'TEST_FAILED'
  | 'RUNTIME_ERROR'
  | 'TIMEOUT'
  | 'RUNNER_ERROR';

/** 턴 이력 한 줄. passedCount가 null이면 "0개 통과"가 아니라 "채점 없음"이다. */
export interface RelayTurnRecord {
  turnIndex: number;
  seatOrder: number;
  lap: number;
  authorUserId: number;
  passedCount: number | null;
  totalCount: number | null;
  /** 직전 통과 수 대비 증가분 = 기여도. 음수일 수 있다. 기준이 없으면 null. */
  delta: number | null;
  /** 치지 않고 건너뛴 턴(이탈·입력 마감 초과). 채점 없음(passedCount null)과 다르다. */
  skipped: boolean;
  startedAt: string;
  finishedAt: string | null;
}

export interface RelayTurnListResponse {
  turns: RelayTurnRecord[];
}

export interface RelayFailedCase {
  name: string;
  message: string | null;
}

/** grading.finished 이벤트 payload. 채점 결과와 이 주자의 기여도. */
export interface RelayGradingOutcome {
  turnIndex: number;
  authorUserId: number;
  runStatus: RelayRunStatus | null;
  passed: number | null;
  total: number | null;
  delta: number | null;
  failedCases: RelayFailedCase[];
  /** 채점 결과를 얻지 못하고 전진했는지. */
  skipped: boolean;
}

export interface RelayTurnFeedback {
  turnIndex: number;
  seatOrder: number;
  lap: number;
  authorUserId: number;
  nickname: string;
  feedback: string | null;
  passedCount: number | null;
  totalCount: number | null;
  delta: number | null;
}

/** 게임 피드백. 턴별 피드백이 곧 주자별 피드백이다. */
export interface RelayFeedback {
  overall: string;
  turns: RelayTurnFeedback[];
}

export interface IceServer {
  urls: string[];
  username: string | null;
  credential: string | null;
}

/** 응답을 그대로 RTCPeerConnection 생성자에 넣는다. */
export interface IceServersResponse {
  iceServers: IceServer[];
}

/* ---------- WebSocket 이벤트 ---------- */

/** 시그널 전달 실패. 대상이 접속 중이 아닐 때 보낸 쪽에만 돌아온다. */
export interface RelaySignalError {
  type: string;
  targetUserId: number | null;
  reason: string;
}

/** 시그널 수신 payload. fromUserId는 서버가 세션에서 확정한 값이다. */
export interface RelaySignalPayload {
  fromUserId: number;
  data: unknown;
}

/**
 * 서버가 밀어주는 이벤트 봉투. type으로 분기한다.
 * 게임 상태 이벤트 뒤에는 항상 room.state가 따라오므로, 화면 상태는 room.state로만
 * 갱신하고 나머지 이벤트는 부가 정보(요약·점수·시그널)로 쓰면 어긋날 일이 없다.
 */
export type RelayEvent =
  | { type: 'room.state'; payload: RelayRoom }
  | { type: 'turn.finished'; payload: RelayTurnSummary }
  | { type: 'turn.failed'; payload: { turnIndex: number; authorUserId: number } }
  | { type: 'turn.skipped'; payload: { turnIndex: number; authorUserId: number } }
  | { type: 'grading.started'; payload: { turnIndex: number } }
  | { type: 'grading.finished'; payload: RelayGradingOutcome }
  | { type: 'feedback.ready'; payload: RelayRoom }
  | { type: 'feedback.failed'; payload: RelayRoom }
  | { type: 'peer.list'; payload: { userIds: number[] } }
  | { type: 'peer.joined'; payload: { userId: number } }
  | { type: 'peer.left'; payload: { userId: number } }
  | { type: 'signal.offer'; payload: RelaySignalPayload }
  | { type: 'signal.answer'; payload: RelaySignalPayload }
  | { type: 'signal.ice'; payload: RelaySignalPayload }
  | { type: 'signal.error'; payload: RelaySignalError };

export type RelaySignalType = 'signal.offer' | 'signal.answer' | 'signal.ice';

/** DataChannel로 피어끼리 직접 주고받는 메시지. 서버는 이 채널을 모른다. */
export type RelayDataMessage =
  | { kind: 'typing'; text: string }
  | { kind: 'reaction'; emoji: string }
  | { kind: 'voice'; joined: boolean; micOn: boolean };
