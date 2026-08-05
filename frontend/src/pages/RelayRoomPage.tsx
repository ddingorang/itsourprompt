import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type RefObject,
} from 'react';
import ReactMarkdown from 'react-markdown';
import { useNavigate, useParams } from 'react-router-dom';

import { useAuth } from '../features/auth/AuthContext';
import PromptFeedback from '../features/feedback/PromptFeedback';
import { getProblemDetail } from '../features/problem/api';
import type { ProblemDetail } from '../features/problem/types';
import {
  getRelayFeedback,
  leaveRelayRoom,
  retryRelayFeedback,
  startRelayGame,
} from '../features/relay/api';
import type {
  RelayEvent,
  RelayFeedback,
  RelayParticipant,
  RelayRoom,
  RelayRoomStatus,
  RelayTurnRecord,
} from '../features/relay/types';
import { useRelayRoom } from '../features/relay/useRelayRoom';
import { useRelayRtc, type RelayPeerView } from '../features/relay/useRelayRtc';
import CodeViewer from '../features/workspace/CodeViewer';
import { ApiError } from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Header from '../shared/components/Header';

const labelClasses =
  'font-mono text-sm leading-[1.5] font-bold tracking-[0.08em] text-[#d6ff50]';

const waitingTitleClasses =
  'font-mono text-[clamp(32px,4vw,44px)] leading-[0.9] font-bold ' +
  'tracking-[-0.04em] whitespace-nowrap text-[#d6ff50]';

const smallLabelClasses = 'font-mono text-[14px] font-bold tracking-[0.12em] text-[#777]';

const waitingSectionLabelClasses =
  'font-mono text-[16px] font-bold tracking-[0.12em] text-[#d6ff50]';

const pageClasses =
  'flex h-screen min-w-80 flex-col overflow-hidden bg-[#090909] text-[#f5f5ef] ' +
  "[font-family:Arial,'Noto_Sans_KR',sans-serif] " +
  'max-[900px]:h-auto max-[900px]:min-h-screen max-[900px]:overflow-visible';

const centeredNoticeClasses =
  'grid min-h-dvh place-items-center bg-[#090909] p-10 text-center ' +
  'font-mono text-xs leading-[1.8] text-[#a3a3a3]';

/** 진행 단계를 대기자 화면에 풀어 쓰는 문구. */
const statusBanners: Record<RelayRoomStatus, string> = {
  WAITING: '참가자를 기다리는 중',
  PLAYING: '주자가 프롬프트를 준비하는 중',
  TURN_GENERATING: 'AI가 코드를 생성하는 중… (수십 초)',
  TURN_GRADING: '빌드/테스트 채점 중…',
  FEEDBACK_GENERATING: '모든 턴 종료 — 피드백을 생성하는 중…',
  FINISHED: '게임 종료',
};

function parseRouteId(param: string | undefined): number | null {
  if (!param) return null;
  const id = Number(param);
  return Number.isInteger(id) && id > 0 ? id : null;
}

/** 주자별 기여도 합계. 채점을 얻지 못한 턴(delta null)은 계산에서 빠진다. */
function totalScores(turns: RelayTurnRecord[]): Map<number, number> {
  const scores = new Map<number, number>();
  turns.forEach((turn) => {
    if (turn.delta !== null) {
      scores.set(turn.authorUserId, (scores.get(turn.authorUserId) ?? 0) + turn.delta);
    }
  });
  return scores;
}

type RelayFileTreeNode = {
  children: RelayFileTreeNode[];
  name: string;
  path: string;
  type: 'file' | 'folder';
};

function createRelayFileTree(paths: string[]): RelayFileTreeNode[] {
  const root: RelayFileTreeNode[] = [];

  [...paths].sort((a, b) => a.localeCompare(b)).forEach((path) => {
    const normalizedPath = path.replaceAll('\\', '/').replace(/^\/+|\/+$/g, '');
    const segments = normalizedPath.split('/').filter(Boolean);
    let children = root;

    segments.forEach((segment, index) => {
      const isFile = index === segments.length - 1;
      const nodePath = segments.slice(0, index + 1).join('/');
      let node = children.find(
        (item) => item.name === segment && item.type === (isFile ? 'file' : 'folder'),
      );

      if (!node) {
        node = {
          children: [],
          name: segment,
          path: isFile ? path : nodePath,
          type: isFile ? 'file' : 'folder',
        };
        children.push(node);
      }

      children = node.children;
    });
  });

  const sortNodes = (nodes: RelayFileTreeNode[]) => {
    nodes.sort((a, b) => {
      if (a.type !== b.type) return a.type === 'folder' ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
    nodes.forEach((node) => sortNodes(node.children));
  };

  sortNodes(root);
  return root;
}

export default function RelayRoomPage() {
  const roomId = parseRouteId(useParams().roomId);
  const { user } = useAuth();

  if (roomId === null) {
    return (
      <div className={centeredNoticeClasses}>
        <div>
          <p className="m-0">잘못된 방 주소입니다.</p>
          <Button className="mt-5" to="/relay" variant="secondary">
            릴레이 입구로
          </Button>
        </div>
      </div>
    );
  }

  // ProtectedRoute가 감싸므로 user는 항상 있지만, 훅 규칙 때문에 분기 아래로 내려
  // 별도 컴포넌트에서 훅을 쓴다.
  return <RelayRoomScreen myUserId={user?.id ?? null} roomId={roomId} />;
}

function RelayRoomScreen({
  myUserId,
  roomId,
}: {
  myUserId: number | null;
  roomId: number;
}) {
  // RTC 훅은 시그널 전송 함수가 필요해 소켓 훅 다음에 만들어진다. 소켓 훅이 peer/signal
  // 이벤트를 넘길 자리를 ref로 먼저 마련하고, RTC 훅이 만들어진 뒤 채운다.
  const rtcHandlerRef = useRef<(event: RelayEvent) => void>(() => {});
  const roomState = useRelayRoom(roomId, rtcHandlerRef);
  const rtc = useRelayRtc(myUserId, roomState.sendSignal);

  useEffect(() => {
    rtcHandlerRef.current = rtc.handleEvent;
  }, [rtc.handleEvent]);

  const { joinError, room, socketStatus } = roomState;

  // 문제 명세. 주자가 뭘 풀어야 하는지 봐야 프롬프트를 쓸 수 있다.
  // 방의 문제는 게임 내내 같으므로 problemId 기준으로 한 번만 읽는다.
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const problemId = room?.problemId;

  useEffect(() => {
    if (problemId === undefined) return;

    const controller = new AbortController();
    void getProblemDetail(problemId, controller.signal)
      .then(setProblem)
      .catch(() => {
        // 명세 없이도 게임은 진행된다. 다음 마운트에서 다시 시도된다.
      });
    return () => controller.abort();
  }, [problemId]);

  if (joinError) {
    return (
      <div className={centeredNoticeClasses}>
        <div>
          <p className="m-0 text-[#ff786b]">{joinError}</p>
          <Button className="mt-5" to="/relay" variant="secondary">
            릴레이 입구로
          </Button>
        </div>
      </div>
    );
  }

  if (socketStatus === 'replaced') {
    return (
      <div className={centeredNoticeClasses}>
        <div>
          <p className="m-0">
            다른 탭(또는 창)에서 이 방에 접속해 이 화면의 연결이 종료됐습니다.
          </p>
          <p className="mt-2 mb-0 text-[#666]">
            게임은 새 탭에서 계속됩니다. 이 탭에서 이어가려면 새로고침하세요.
          </p>
        </div>
      </div>
    );
  }

  if (!room) {
    return (
      <div className={centeredNoticeClasses}>
        <p className="m-0">방에 입장하는 중…</p>
      </div>
    );
  }

  const isWaiting = room.status === 'WAITING';

  return (
    <div
      className={`${pageClasses} ${
        isWaiting ? '!overflow-x-hidden !overflow-y-auto' : ''
      }`}
    >
      <Header variant={isWaiting ? 'default' : 'workspace'} />
      {isWaiting ? (
        <WaitingView myUserId={myUserId} problem={problem} room={room} rtc={rtc} />
      ) : room.status === 'FINISHED' ? (
        <FinishedView room={room} roomState={roomState} />
      ) : (
        <GameView
          myUserId={myUserId}
          problem={problem}
          room={room}
          roomState={roomState}
          rtc={rtc}
        />
      )}
      <PeerAudios peers={rtc.peers} />
    </div>
  );
}

/* ---------- 대기실 ---------- */

function WaitingView({
  myUserId,
  problem,
  room,
  rtc,
}: {
  myUserId: number | null;
  problem: ProblemDetail | null;
  room: RelayRoom;
  rtc: ReturnType<typeof useRelayRtc>;
}) {
  const navigate = useNavigate();
  const [actionError, setActionError] = useState<string | null>(null);
  const [starting, setStarting] = useState(false);

  const isHost = room.hostUserId === myUserId;
  const enough = room.participants.length >= 2;

  const handleStart = async () => {
    setActionError(null);
    setStarting(true);
    try {
      await startRelayGame(room.roomId);
      // 화면 전환은 소켓의 room.state가 한다 — 응답으로 바꾸면 두 경로가 경합한다.
    } catch (error) {
      setActionError(
        error instanceof ApiError ? error.message : '게임을 시작하지 못했습니다.',
      );
      setStarting(false);
    }
  };

  const handleLeave = async () => {
    try {
      await leaveRelayRoom(room.roomId);
    } finally {
      navigate('/relay');
    }
  };

  return (
    <main className="mx-auto grid w-full max-w-[720px] flex-1 content-start gap-7 px-6 py-10">
      <div>
        <div className="flex items-end justify-between gap-6 max-[640px]:flex-col max-[640px]:items-start max-[640px]:gap-3">
          <div className={waitingTitleClasses}>ROOM #{room.roomId}</div>
          <div className="shrink-0 text-right font-mono text-[12px] leading-[1.6] tracking-[0.04em] text-[#a3a3a3] max-[640px]:text-left">
            {problem ? `「${problem.title}」` : `문제 ${room.problemId}번`} ·{' '}
            {room.totalLaps}바퀴 · 정원 {room.maxParticipants}명
          </div>
        </div>
        {/* 이름 도입 전에 만들어진 방은 name이 없다 — 그때는 방 번호 제목만으로 충분하다. */}
        {room.name && (
          <h1 className="mt-2 mb-0 text-[22px] font-black tracking-[-0.03em]">
            {room.name}
          </h1>
        )}
        <p className="mt-2 mb-0 text-[13px] leading-[1.7] text-[#a3a3a3]">
          이 방 번호를 공유하면 다른 사람이 입장할 수 있습니다.
          <br />
          입장한 순서가 곧 풀이 순서가 됩니다.
        </p>
      </div>

      {problem && (
        <section className="border border-[#343434]">
          <div className="border-b border-[#343434] px-4 py-3">
            <span className={waitingSectionLabelClasses}>PROBLEM</span>
          </div>
          <div className="max-h-[320px] overflow-y-auto px-5 py-4">
            <ProblemSpec specMd={problem.specMd} />
          </div>
        </section>
      )}

      <section className="border border-[#343434]">
        <div className="border-b border-[#343434] px-4 py-3">
          <span className={waitingSectionLabelClasses}>
            PLAYERS: {room.participants.length} / {room.maxParticipants}
          </span>
        </div>
        <ul className="m-0 grid list-none gap-0 p-0">
          {room.participants.map((participant, index) => (
            <li
              className="flex items-center gap-3 border-b border-[#222] px-4 py-3 font-mono text-sm last:border-b-0"
              key={participant.userId}
            >
              <span className="text-[#666]">{index + 1}</span>
              <span>{participant.nickname}</span>
              {participant.userId === room.hostUserId && (
                <span className="border border-[#d6ff50] px-1.5 py-0.5 text-[9px] text-[#d6ff50]">
                  HOST
                </span>
              )}
              {participant.userId === myUserId && (
                <span className="text-[10px] text-[#777]">(나)</span>
              )}
              <VoiceDot peer={rtc.peers.get(participant.userId)} self={participant.userId === myUserId} />
            </li>
          ))}
        </ul>
      </section>

      <VoicePanel labelClassName={waitingSectionLabelClasses} rtc={rtc} />

      {actionError && (
        <p className="m-0 font-mono text-xs text-[#ff786b]">{actionError}</p>
      )}

      <div className="flex justify-end gap-3">
        {isHost &&
          (enough ? (
            <Button disabled={starting} onClick={() => void handleStart()}>
              {starting ? '시작하는 중…' : '게임 시작'}
            </Button>
          ) : (
            <span
              aria-disabled="true"
              className="inline-flex min-h-11 cursor-not-allowed items-center justify-center border border-[#3f3f3f] bg-[#171717] px-[18px] text-[14px] leading-none font-extrabold tracking-[-0.01em] text-[#666]"
            >
              2명 이상 모여야 시작할 수 있습니다
            </span>
          ))}
        {!isHost && (
          <p className="m-0 self-center font-mono text-xs text-[#a3a3a3]">
            방장이 시작하면 자동으로 게임 화면으로 넘어갑니다.
          </p>
        )}
        <Button
          className="hover:!border-[#d6ff50] hover:!bg-transparent hover:!text-[#d6ff50] focus-visible:!border-[#d6ff50] focus-visible:!bg-transparent focus-visible:!text-[#d6ff50]"
          onClick={() => void handleLeave()}
          variant="ghost"
        >
          나가기
        </Button>
      </div>
    </main>
  );
}

/* ---------- 게임 화면 ---------- */

function GameView({
  myUserId,
  problem,
  room,
  roomState,
  rtc,
}: {
  myUserId: number | null;
  problem: ProblemDetail | null;
  room: RelayRoom;
  roomState: ReturnType<typeof useRelayRoom>;
  rtc: ReturnType<typeof useRelayRtc>;
}) {
  const {
    code,
    lastGrading,
    lastSkip,
    lastTurn,
    submitError,
    submitTurn,
    submitting,
    turns,
  } = roomState;
  const [panelTab, setPanelTab] = useState<'problem' | 'live'>('problem');
  const [selectedPath, setSelectedPath] = useState<string | null>(null);

  const me = room.participants.find((participant) => participant.userId === myUserId);
  const isMyTurn =
    room.status === 'PLAYING' &&
    me?.seatOrder !== null &&
    me?.seatOrder !== undefined &&
    me.seatOrder === room.currentSeat;

  const currentRunner = room.participants.find(
    (participant) => participant.seatOrder === room.currentSeat,
  );
  const scores = useMemo(() => totalScores(turns), [turns]);

  const seated = useMemo(
    () =>
      [...room.participants].sort(
        (a, b) => (a.seatOrder ?? 0) - (b.seatOrder ?? 0),
      ),
    [room.participants],
  );

  return (
    <main className="grid min-h-0 flex-1 overflow-hidden grid-cols-[250px_minmax(320px,1fr)_minmax(380px,440px)] max-[900px]:block max-[900px]:overflow-visible">
      {/* 좌: 좌석과 점수 */}
      <aside className="flex min-h-0 flex-col gap-5 overflow-hidden border-r border-[#343434] px-5 py-[22px] max-[900px]:overflow-visible max-[900px]:border-r-0 max-[900px]:border-b">
        <div>
          <div className={labelClasses}>{room.name ?? `ROOM #${room.roomId}`}</div>
          <p className="mt-1 mb-0 font-mono text-[10px] text-[#777]">
            {room.name ? `#${room.roomId} · ` : ''}TURN {room.currentTurnIndex + 1} /{' '}
            {room.totalTurns ?? '?'} · LAP {(room.currentLap ?? 0) + 1} / {room.totalLaps}
          </p>
        </div>

        <div className="grid gap-2">
          {seated.map((participant) => (
            <SeatCard
              current={participant.seatOrder === room.currentSeat}
              key={participant.userId}
              me={participant.userId === myUserId}
              participant={participant}
              peer={rtc.peers.get(participant.userId)}
              score={scores.get(participant.userId) ?? null}
            />
          ))}
        </div>

        {room.baselinePassed !== null && (
          <p className="m-0 font-mono text-[10px] leading-[1.7] text-[#666]">
            BASELINE {room.baselinePassed}/{room.baselineTotal} — 시작 스켈레톤이
            이미 통과한 테스트. 첫 주자의 기여도 기준선.
          </p>
        )}

        <VoicePanel rtc={rtc} />

        <RelayFileExplorer
          changedPaths={lastTurn?.changedPaths ?? []}
          files={code?.files ?? []}
          onSelect={setSelectedPath}
          selectedPath={selectedPath}
        />

        <LeaveGameButton roomId={room.roomId} />
      </aside>

      {/* 중: 코드 */}
      <CodePanel
        changedPaths={lastTurn?.changedPaths ?? []}
        code={code}
        onSelect={setSelectedPath}
        selectedPath={selectedPath}
      />

      {/* 우: 문제/진행 패널. 프롬프트 폼은 탭과 무관하게 아래 고정 —
          주자는 명세를 읽으면서 동시에 프롬프트를 써야 한다. */}
      <aside className="flex min-h-0 flex-col gap-4 overflow-hidden px-5 py-[22px] max-[900px]:overflow-visible max-[900px]:border-t max-[900px]:border-[#343434]">
        <StatusBanner room={room} runnerNickname={currentRunner?.nickname ?? null} />

        <div className="grid shrink-0 grid-cols-2 border border-[#3f3f3f]" role="tablist">
          {(
            [
              ['problem', 'PROBLEM'],
              ['live', 'LIVE'],
            ] as const
          ).map(([tab, label]) => (
            <button
              aria-selected={panelTab === tab}
              className={[
                'min-h-9 cursor-pointer border-0 bg-transparent px-3 font-mono text-xs font-bold tracking-[0.08em]',
                tab === 'problem' ? 'border-r border-[#3f3f3f]' : '',
                panelTab === tab
                  ? 'border-b-2 border-b-[#d6ff50] text-[#d6ff50]'
                  : 'text-[#8b8b8b] hover:text-[#b8b8b8]',
              ].join(' ')}
              key={tab}
              onClick={() => setPanelTab(tab)}
              role="tab"
              type="button"
            >
              {label}
            </button>
          ))}
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto max-[900px]:min-h-[220px]">
          {panelTab === 'problem' ? (
            problem ? (
              <ProblemSpec specMd={problem.specMd} />
            ) : (
              <p className="m-0 font-mono text-[11px] text-[#666]">
                문제 명세를 불러오는 중…
              </p>
            )
          ) : (
            <div className="grid gap-4">
              {lastSkip && (
                <section className="border border-[#4a3a1e] bg-[#171207] px-4 py-3">
                  <p className="m-0 font-mono text-[11px] font-bold text-[#ffb86b]">
                    TURN {String(lastSkip.turnIndex + 1).padStart(2, '0')} 건너뜀 —{' '}
                    {room.participants.find((p) => p.userId === lastSkip.authorUserId)
                      ?.nickname ?? `user ${lastSkip.authorUserId}`}{' '}
                    님 (이탈 또는 시간 초과)
                  </p>
                </section>
              )}
              {lastGrading && <GradingCard outcome={lastGrading} room={room} />}
              {lastTurn && (
                <section className="border-l-2 border-[#d6ff50] pl-3">
                  <div className="font-mono text-[11px] font-bold text-[#d6ff50]">
                    TURN {String(lastTurn.turnIndex + 1).padStart(2, '0')} 완료
                  </div>
                  <p className="my-2 whitespace-pre-wrap text-[12px] leading-[1.6] text-[#8f8f8f]">
                    {lastTurn.aiSummary}
                  </p>
                </section>
              )}
              {!lastGrading && !lastTurn && !lastSkip && (
                <p className="m-0 font-mono text-[11px] leading-[1.7] text-[#666]">
                  아직 완료된 턴이 없습니다. 턴이 끝나면 AI 요약과 채점 결과가
                  여기 표시됩니다.
                </p>
              )}
            </div>
          )}
        </div>

        {!isMyTurn && currentRunner && room.status === 'PLAYING' && (
          <TypingPreview peer={rtc.peers.get(currentRunner.userId)} runner={currentRunner} />
        )}

        <ReactionBar rtc={rtc} />

        <PromptForm
          isMyTurn={isMyTurn}
          onTyping={rtc.sendTyping}
          roomStatus={room.status}
          submitError={submitError}
          submitTurn={submitTurn}
          submitting={submitting}
        />
      </aside>
    </main>
  );
}

/**
 * 게임 중 퇴장. 대기실 퇴장과 달리 좌석이 남고 내 차례가 자동으로 건너뛰어지므로,
 * 실수 클릭 한 번으로 나가지 않게 인라인 확인을 한 단계 거친다. 서버는 게임이 끝나기
 * 전에 다시 입장하면 이탈 표시를 지워 주므로(rejoin) 복귀 가능함을 함께 안내한다.
 */
function LeaveGameButton({ roomId }: { roomId: number }) {
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
  const [leaving, setLeaving] = useState(false);

  const handleLeave = async () => {
    setLeaving(true);
    try {
      await leaveRelayRoom(roomId);
    } finally {
      navigate('/relay');
    }
  };

  if (!confirming) {
    return (
      <Button className="mt-auto" onClick={() => setConfirming(true)} variant="ghost">
        나가기
      </Button>
    );
  }

  return (
    <div className="mt-auto grid gap-2">
      <p className="m-0 font-mono text-[10px] leading-[1.7] text-[#ffb86b]">
        게임 중에 나가면 내 차례는 건너뛰어집니다. 게임이 끝나기 전에 다시
        입장하면 이어서 참여할 수 있습니다.
      </p>
      <div className="flex gap-2">
        <Button disabled={leaving} onClick={() => void handleLeave()} variant="secondary">
          {leaving ? '나가는 중…' : '나가기'}
        </Button>
        <Button disabled={leaving} onClick={() => setConfirming(false)} variant="ghost">
          계속하기
        </Button>
      </div>
    </div>
  );
}

function SeatCard({
  current,
  me,
  participant,
  peer,
  score,
}: {
  current: boolean;
  me: boolean;
  participant: RelayParticipant;
  peer: RelayPeerView | undefined;
  score: number | null;
}) {
  return (
    <div
      className={[
        'border px-3 py-2.5',
        current ? 'border-[#d6ff50] bg-[#161a08]' : 'border-[#2c2c2c]',
        participant.left ? 'opacity-50' : '',
      ].join(' ')}
    >
      <div className="flex items-center gap-2 font-mono text-xs">
        <span className="text-[#666]">#{(participant.seatOrder ?? 0) + 1}</span>
        <span className={current ? 'font-bold text-[#d6ff50]' : ''}>
          {participant.nickname}
        </span>
        {me && <span className="text-[9px] text-[#777]">(나)</span>}
        {participant.left && <span className="text-[9px] text-[#ff786b]">이탈</span>}
        <VoiceDot peer={peer} self={me} />
        {peer?.reaction && <span className="text-base">{peer.reaction}</span>}
        <span className="ml-auto font-bold text-[#c7c7c2]">
          {score === null ? '—' : score > 0 ? `+${score}` : `${score}`}
        </span>
      </div>
    </div>
  );
}

function StatusBanner({
  room,
  runnerNickname,
}: {
  room: RelayRoom;
  runnerNickname: string | null;
}) {
  return (
    <div className="border border-[#3f3f3f] bg-[#111] px-4 py-3">
      <div className="flex items-baseline justify-between gap-3">
        <p className={`m-0 ${smallLabelClasses}`}>STATUS</p>
        {room.status === 'PLAYING' && room.turnDeadline && (
          <TurnCountdown deadline={room.turnDeadline} />
        )}
      </div>
      <p className="mt-1 mb-0 text-[13px] text-[#f5f5ef]">
        {room.status === 'PLAYING' && runnerNickname
          ? `${runnerNickname} 님의 차례`
          : statusBanners[room.status]}
      </p>
    </div>
  );
}

/**
 * 입력 마감까지 남은 시간. 넘기면 서버가 그 턴을 건너뛴다 — 주자를 재촉하고,
 * 대기자에게는 "최악의 경우 언제 넘어가는지"를 알려준다. 시간의 진실은 서버의
 * turnDeadline이고 이 컴포넌트는 그걸 초 단위로 그려줄 뿐이다.
 */
function TurnCountdown({ deadline }: { deadline: string }) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const remainingMs = new Date(deadline).getTime() - now;

  // 마감을 지나면 다음 스케줄러 주기(최대 15초)에 스킵된다. 0:00으로 굳는 것보다
  // 무슨 일이 일어날지 말해주는 편이 낫다.
  if (remainingMs <= 0) {
    return <span className="font-mono text-[11px] text-[#ff786b]">곧 건너뜁니다…</span>;
  }

  const totalSeconds = Math.floor(remainingMs / 1000);
  const urgent = totalSeconds <= 30;

  return (
    <span
      className={`font-mono text-[11px] font-bold ${urgent ? 'text-[#ff786b]' : 'text-[#a3a3a3]'}`}
    >
      ⏱ {Math.floor(totalSeconds / 60)}:{String(totalSeconds % 60).padStart(2, '0')}
    </span>
  );
}

function GradingCard({
  outcome,
  room,
}: {
  outcome: NonNullable<ReturnType<typeof useRelayRoom>['lastGrading']>;
  room: RelayRoom;
}) {
  const author = room.participants.find(
    (participant) => participant.userId === outcome.authorUserId,
  );

  return (
    <section className="border border-[#3f3f3f] px-4 py-3">
      <div className="flex items-baseline justify-between font-mono text-[11px] font-bold">
        <span className="text-[#d6ff50]">
          채점 — TURN {String(outcome.turnIndex + 1).padStart(2, '0')} (
          {author?.nickname ?? `user ${outcome.authorUserId}`})
        </span>
        {outcome.skipped ? (
          <span className="text-[#ffb86b]">채점 없음</span>
        ) : (
          <span className="text-[#c7c7c2]">
            {outcome.passed}/{outcome.total} 통과 ·{' '}
            <strong
              className={
                (outcome.delta ?? 0) < 0 ? 'text-[#ff786b]' : 'text-[#d6ff50]'
              }
            >
              {outcome.delta === null
                ? '기여도 —'
                : `기여도 ${outcome.delta > 0 ? '+' : ''}${outcome.delta}`}
            </strong>
          </span>
        )}
      </div>

      {outcome.failedCases.length > 0 && (
        <ul className="mt-2 mb-0 grid list-none gap-1 p-0 font-mono text-[10px] leading-[1.5] text-[#ff786b]">
          {outcome.failedCases.map((failed) => (
            <li key={failed.name}>✗ {failed.name}</li>
          ))}
        </ul>
      )}
    </section>
  );
}

function TypingPreview({
  peer,
  runner,
}: {
  peer: RelayPeerView | undefined;
  runner: RelayParticipant;
}) {
  return (
    <section className="border border-[#2c2c2c] bg-[#111] px-4 py-3">
      <p className={`m-0 ${smallLabelClasses}`}>
        {runner.nickname} 님이 입력 중 (실시간 P2P)
      </p>
      <p className="mt-2 mb-0 min-h-[1.6em] text-[12px] leading-[1.6] text-[#a3a3a3] italic">
        {peer?.typing || '…'}
      </p>
    </section>
  );
}

const REACTIONS = ['👍', '🔥', '😱', '🤔', '👏'] as const;

function ReactionBar({ rtc }: { rtc: ReturnType<typeof useRelayRtc> }) {
  return (
    <div className="flex gap-2">
      {REACTIONS.map((emoji) => (
        <button
          className="cursor-pointer border border-[#3f3f3f] bg-transparent px-2.5 py-1.5 text-base hover:border-[#d6ff50]"
          key={emoji}
          onClick={() => rtc.sendReaction(emoji)}
          type="button"
        >
          {emoji}
        </button>
      ))}
    </div>
  );
}

function PromptForm({
  isMyTurn,
  onTyping,
  roomStatus,
  submitError,
  submitTurn,
  submitting,
}: {
  isMyTurn: boolean;
  onTyping: (text: string) => void;
  roomStatus: RelayRoomStatus;
  submitError: string | null;
  submitTurn: (prompt: string) => Promise<void>;
  submitting: boolean;
}) {
  const [prompt, setPrompt] = useState('');

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const trimmed = prompt.trim();
    if (!trimmed || submitting) return;

    void submitTurn(trimmed).then(() => {
      setPrompt('');
      onTyping('');
    });
  };

  const disabled = !isMyTurn || submitting;

  return (
    <form className="mt-auto grid gap-2" onSubmit={handleSubmit}>
      <label className={smallLabelClasses} htmlFor="relay-prompt">
        PROMPT {isMyTurn ? '— 내 차례!' : ''}
      </label>
      <textarea
        className="min-h-[110px] w-full resize-y border border-[#3f3f3f] bg-[#151515] p-3 text-[13px] leading-[1.6] text-[#f5f5ef] placeholder:text-[#555] focus:border-[#d6ff50] focus:outline-none disabled:opacity-40"
        disabled={disabled}
        id="relay-prompt"
        onChange={(event) => {
          setPrompt(event.target.value);
          // 대기자들의 화면에 실시간으로 보이는 미리보기. 서버를 지나지 않는다(P2P).
          if (isMyTurn) onTyping(event.target.value);
        }}
        placeholder={
          isMyTurn
            ? '앞사람의 코드를 이어받아 AI에게 시킬 작업을 적으세요'
            : roomStatus === 'PLAYING'
              ? '내 차례가 되면 입력할 수 있습니다'
              : statusBanners[roomStatus]
        }
        value={prompt}
      />
      {submitError && (
        <p className="m-0 font-mono text-xs text-[#ff786b]">{submitError}</p>
      )}
      <Button disabled={disabled || !prompt.trim()} type="submit">
        {submitting ? 'AI가 생성하는 중…' : '턴 전송'}
      </Button>
    </form>
  );
}

/* ---------- 문제 명세 ---------- */

/** 문제 명세 마크다운. ProblemDetailPage의 PROBLEM 탭과 같은 시각 규칙을 따른다. */
function ProblemSpec({ specMd }: { specMd: string }) {
  return (
    <div className="whitespace-pre-wrap text-[13px] leading-[1.7] text-[#a3a3a3] [word-break:keep-all]">
      <ReactMarkdown
        components={{
          h1: ({ children }) => (
            <h1 className="my-3 mt-2.5 text-[20px] leading-[1.1] font-bold tracking-[-0.045em] text-[#f5f5ef]">
              {children}
            </h1>
          ),
          h2: ({ children }) => <h2 className="font-bold text-[#f5f5ef]">{children}</h2>,
          h3: ({ children }) => <h3 className="font-bold text-[#f5f5ef]">{children}</h3>,
          h4: ({ children }) => <h4 className="font-bold text-[#f5f5ef]">{children}</h4>,
          h5: ({ children }) => <h5 className="font-bold text-[#f5f5ef]">{children}</h5>,
          h6: ({ children }) => <h6 className="font-bold text-[#f5f5ef]">{children}</h6>,
        }}
      >
        {specMd}
      </ReactMarkdown>
    </div>
  );
}

/* ---------- 코드 패널 ---------- */

function RelayFileExplorer({
  changedPaths,
  files,
  onSelect,
  selectedPath,
}: {
  changedPaths: string[];
  files: NonNullable<ReturnType<typeof useRelayRoom>['code']>['files'];
  onSelect: (path: string) => void;
  selectedPath: string | null;
}) {
  const fileTree = useMemo(
    () => createRelayFileTree(files.map((file) => file.path)),
    [files],
  );
  const activePath = selectedPath ?? files[0]?.path ?? null;

  const renderNodes = (nodes: RelayFileTreeNode[], depth = 0): React.ReactNode =>
    nodes.map((node) => {
      if (node.type === 'folder') {
        return (
          <div key={node.path}>
            <div
              className="flex min-h-7 items-center gap-2 whitespace-nowrap font-mono text-[12px] text-[#a3a3a3]"
              style={{ paddingLeft: `${8 + depth * 14}px` }}
            >
              <span aria-hidden="true" className="text-[10px] text-[#777]">▼</span>
              <span aria-hidden="true" className="text-[#d6ff50]">▱</span>
              <span>{node.name}</span>
            </div>
            {renderNodes(node.children, depth + 1)}
          </div>
        );
      }

      const selected = node.path === activePath;
      return (
        <button
          className={`grid min-h-8 w-full cursor-pointer grid-cols-[14px_minmax(0,1fr)_14px] items-center gap-2 border-0 px-2 text-left font-mono text-[12px] ${
            selected
              ? 'bg-[#d6ff50] text-[#090909]'
              : 'bg-transparent text-[#a3a3a3] hover:text-[#d6ff50]'
          }`}
          key={node.path}
          onClick={() => onSelect(node.path)}
          style={{ paddingLeft: `${8 + depth * 14}px` }}
          title={node.path}
          type="button"
        >
          <span aria-hidden="true">◇</span>
          <span className="truncate">{node.name}</span>
          <span className="text-right font-black">
            {changedPaths.includes(node.path) ? 'M' : ''}
          </span>
        </button>
      );
    });

  return (
    <section className="flex min-h-0 flex-1 flex-col border border-[#2c2c2c] max-[900px]:max-h-[320px] max-[900px]:min-h-[180px]">
      <div className="shrink-0 border-b border-[#2c2c2c] px-4 py-3 font-mono text-[14px] font-bold tracking-[0.12em] text-[#d6ff50]">
        FILE EXPLORER
      </div>
      <div className="workspace-scrollbar min-h-0 flex-1 overflow-y-auto overflow-x-hidden py-2">
        {fileTree.length > 0 ? (
          renderNodes(fileTree)
        ) : (
          <p className="m-0 px-4 py-3 font-mono text-[11px] text-[#666]">
            파일을 불러오는 중…
          </p>
        )}
      </div>
    </section>
  );
}

function CodePanel({
  changedPaths,
  code,
  onSelect,
  selectedPath,
}: {
  changedPaths: string[];
  code: ReturnType<typeof useRelayRoom>['code'];
  onSelect: (path: string) => void;
  selectedPath: string | null;
}) {
  const files = code?.files ?? [];
  const selected =
    files.find((file) => file.path === selectedPath) ?? files[0] ?? null;

  return (
    <section className="flex min-h-0 flex-col overflow-hidden border-r border-[#343434] px-6 py-[22px] max-[900px]:min-h-[420px] max-[900px]:border-r-0 max-[900px]:border-b">
      <div className="mb-3 flex flex-wrap items-center gap-2">
        {files.map((file) => (
          <button
            className={[
              'cursor-pointer border bg-transparent px-2.5 py-1.5 font-mono text-[10px]',
              file.path === selected?.path
                ? 'border-[#d6ff50] text-[#d6ff50]'
                : 'border-[#3f3f3f] text-[#a3a3a3] hover:text-[#d6ff50]',
              changedPaths.includes(file.path) ? 'font-bold' : '',
            ].join(' ')}
            key={file.path}
            onClick={() => onSelect(file.path)}
            type="button"
          >
            {changedPaths.includes(file.path) ? '● ' : ''}
            {file.path.split('/').pop()}
          </button>
        ))}
        <span className="ml-auto border border-[#494949] px-2 py-1.5 font-mono text-[9px] text-[#a3a3a3]">
          READ ONLY · {code ? `${code.appliedTurns}턴 반영` : '로딩 중'}
        </span>
      </div>

      <div className="min-h-0 flex-1 overflow-auto border border-[#292929] bg-[#202020]">
        {selected ? (
          <CodeViewer
            code={selected.content}
            gutterWidth="2.5rem"
            path={selected.path}
          />
        ) : (
          <div className="grid h-full place-items-center font-mono text-[11px] text-[#666]">
            코드를 불러오는 중…
          </div>
        )}
      </div>
    </section>
  );
}

/* ---------- 종료(피드백) 화면 ---------- */

function FinishedView({
  room,
  roomState,
}: {
  room: RelayRoom;
  roomState: ReturnType<typeof useRelayRoom>;
}) {
  const { feedbackFailed, turns } = roomState;
  const [feedback, setFeedback] = useState<RelayFeedback | null>(null);
  const [feedbackError, setFeedbackError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void getRelayFeedback(room.roomId, controller.signal)
      .then(setFeedback)
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setFeedbackError(
            error instanceof ApiError ? error.message : '피드백을 불러오지 못했습니다.',
          );
        }
      });
    return () => controller.abort();
  }, [room.roomId]);

  const scores = useMemo(() => totalScores(turns), [turns]);

  return (
    <main className="mx-auto grid w-full max-w-[820px] flex-1 content-start gap-7 overflow-y-auto px-6 py-10">
      <div>
        <div className={labelClasses}>ROOM #{room.roomId} — FINISHED</div>
        <h1 className="mt-2 mb-0 text-[26px] font-black tracking-[-0.03em]">
          {room.name ? `「${room.name}」 릴레이 결과` : '릴레이 결과'}
        </h1>
      </div>

      <section className="grid gap-2">
        <span className={smallLabelClasses}>SCOREBOARD — 기여도 합계</span>
        {[...room.participants]
          .sort((a, b) => (a.seatOrder ?? 0) - (b.seatOrder ?? 0))
          .map((participant) => {
            const score = scores.get(participant.userId) ?? null;
            return (
              <div
                className="flex items-center gap-3 border border-[#2c2c2c] px-4 py-2.5 font-mono text-sm"
                key={participant.userId}
              >
                <span className="text-[#666]">#{(participant.seatOrder ?? 0) + 1}</span>
                <span>{participant.nickname}</span>
                <span
                  className={`ml-auto font-bold ${
                    score !== null && score < 0 ? 'text-[#ff786b]' : 'text-[#d6ff50]'
                  }`}
                >
                  {score === null ? '—' : score > 0 ? `+${score}` : `${score}`}
                </span>
              </div>
            );
          })}
      </section>

      {feedbackFailed && (
        <section className="border border-[#5a2c28] bg-[#1c0f0e] px-4 py-3">
          <p className="m-0 font-mono text-xs text-[#ff786b]">
            피드백 생성에 실패했습니다.
          </p>
          <Button
            className="mt-3"
            onClick={() => void retryRelayFeedback(room.roomId)}
            variant="secondary"
          >
            다시 생성 요청
          </Button>
        </section>
      )}

      {feedbackError && !feedback && (
        <p className="m-0 font-mono text-xs text-[#ff786b]">{feedbackError}</p>
      )}

      {feedback && (
        <>
          <section className="border border-[#343434] p-5">
            <span className={smallLabelClasses}>OVERALL</span>
            <PromptFeedback feedback={feedback.overall} />
          </section>

          <section className="grid gap-4">
            <span className={smallLabelClasses}>턴별(주자별) 피드백</span>
            {feedback.turns.map((turn) => {
              // 피드백 응답에는 스킵 여부가 없다 — 턴 이력에서 찾아 배지를 단다.
              const skipped = turns.find(
                (record) => record.turnIndex === turn.turnIndex,
              )?.skipped;

              return (
              <article
                className={`border-l-2 pl-4 ${skipped ? 'border-[#4a3a1e] opacity-70' : 'border-[#d6ff50]'}`}
                key={turn.turnIndex}
              >
                <div className="flex flex-wrap items-baseline gap-3 font-mono text-[11px] font-bold">
                  <span className={skipped ? 'text-[#ffb86b]' : 'text-[#d6ff50]'}>
                    TURN {String(turn.turnIndex + 1).padStart(2, '0')}
                  </span>
                  <span className="text-[#f5f5ef]">{turn.nickname}</span>
                  <span className="text-[#777]">
                    {skipped
                      ? '건너뜀 (이탈 또는 시간 초과)'
                      : turn.passedCount === null
                        ? '채점 없음'
                        : `${turn.passedCount}/${turn.totalCount} 통과`}
                  </span>
                  {turn.delta !== null && (
                    <span
                      className={turn.delta < 0 ? 'text-[#ff786b]' : 'text-[#d6ff50]'}
                    >
                      기여도 {turn.delta > 0 ? `+${turn.delta}` : turn.delta}
                    </span>
                  )}
                </div>
                {turn.feedback && (
                  <p className="mt-2 mb-0 text-[13px] leading-[1.7] text-[#c7c7c2] [word-break:keep-all]">
                    {turn.feedback}
                  </p>
                )}
              </article>
              );
            })}
          </section>
        </>
      )}

      <div>
        <Button to="/relay" variant="secondary">
          릴레이 입구로
        </Button>
      </div>
    </main>
  );
}

/* ---------- 음성 ---------- */

function VoicePanel({
  labelClassName = smallLabelClasses,
  rtc,
}: {
  labelClassName?: string;
  rtc: ReturnType<typeof useRelayRtc>;
}) {
  return (
    <section className="grid gap-2 border border-[#2c2c2c] px-4 py-3">
      <span className={labelClassName}>VOICE</span>
      <button
        className={[
          'cursor-pointer border px-3 py-2 font-mono text-[11px] font-bold',
          rtc.audioOn
            ? 'border-[#d6ff50] bg-[#d6ff50] text-[#090909]'
            : 'border-[#3f3f3f] bg-transparent text-[#a3a3a3] hover:border-[#d6ff50] hover:text-[#d6ff50]',
        ].join(' ')}
        onClick={() => void rtc.toggleAudio()}
        type="button"
      >
        {rtc.audioOn ? '🎤 마이크 켜짐 — 누르면 끔' : '🎤 마이크 켜기'}
      </button>
      {rtc.audioError && (
        <p className="m-0 font-mono text-[10px] text-[#ff786b]">{rtc.audioError}</p>
      )}
    </section>
  );
}

/** 피어 연결 상태 점. 초록 = P2P 연결됨. */
function VoiceDot({
  peer,
  self,
}: {
  peer: RelayPeerView | undefined;
  self: boolean;
}) {
  if (self) return null;

  const color =
    peer?.connectionState === 'connected'
      ? 'bg-[#7dd956]'
      : peer?.connectionState === 'failed' || peer?.connectionState === 'disconnected'
        ? 'bg-[#ff786b]'
        : 'bg-[#555]';

  return (
    <span
      aria-label={`연결 상태: ${peer?.connectionState ?? '미연결'}`}
      className={`inline-block h-1.5 w-1.5 rounded-full ${color}`}
      title={peer?.connectionState ?? '미연결'}
    />
  );
}

/**
 * 피어들의 음성 출력. 화면에 보이지 않지만 stream이 있는 피어마다 하나씩 재생한다.
 * ref 콜백에서 srcObject를 잇는다 — audio 엘리먼트는 속성으로 스트림을 못 받는다.
 */
function PeerAudios({ peers }: { peers: Map<number, RelayPeerView> }) {
  return (
    <>
      {[...peers.values()]
        .filter((peer) => peer.stream !== null)
        .map((peer) => (
          <PeerAudio key={peer.userId} stream={peer.stream as MediaStream} />
        ))}
    </>
  );
}

function PeerAudio({ stream }: { stream: MediaStream }) {
  const ref: RefObject<HTMLAudioElement | null> = useRef(null);

  useEffect(() => {
    const audio = ref.current;
    if (audio) {
      audio.srcObject = stream;
      void audio.play().catch(() => {
        // 자동재생이 차단된 경우다. 사용자가 페이지와 상호작용하면 다음 스트림부터 재생된다.
      });
    }
  }, [stream]);

  return <audio autoPlay ref={ref} />;
}
