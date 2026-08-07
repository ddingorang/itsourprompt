import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { getProblems } from '../features/problem/api';
import type { ProblemSummary } from '../features/problem/types';
import { createRelayRoom, getRelayRooms } from '../features/relay/api';
import { formatTurnTimeLimit } from '../features/relay/format';
import type { RelayRoomSummary } from '../features/relay/types';
import { useTheme } from '../features/theme/ThemeContext';
import { ApiError, isAbortError } from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Footer from '../shared/components/Footer';
import Header from '../shared/components/Header';

const labelClasses =
  'text-[20px] leading-[1.5] font-bold text-[#d6ff50]';

const fieldClasses =
  'w-full border border-[#3f3f3f] bg-[#151515] px-3 py-2.5 text-sm ' +
  'text-[#f5f5ef] focus:border-[#d6ff50] focus:outline-none';

/**
 * 네이티브 select의 토글 화살표는 브라우저가 오른쪽 테두리에 붙여 그리고 padding-right를
 * 무시한다 — 기본 화살표를 끄고 배경 이미지로 직접 그려야 위치를 잡을 수 있다.
 */
const selectClasses = `${fieldClasses} appearance-none pr-10`;

/** 테두리에서 14px 떨어뜨린 토글 화살표. 회색이라 라이트 모드에서도 그대로 읽힌다. */
const selectArrowStyle = {
  backgroundImage:
    "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='12' height='8' viewBox='0 0 12 8'><path d='M1 1l5 5 5-5' fill='none' stroke='%23a3a3a3' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'/></svg>\")",
  backgroundPosition: 'right 14px center',
  backgroundRepeat: 'no-repeat',
} as const;

/**
 * 방 목록의 열 구성. 격자를 ul에 두고 각 행은 display:contents로 풀어, 열 폭이
 * 행 단위가 아니라 목록 전체에서 계산되게 한다 — auto 열은 가장 긴 내용에 딱 맞춰
 * 잡히므로 정렬을 지키면서도 남는 여백이 생기지 않는다.
 *
 * 번호·방 이름·문제 이름·방장·바퀴·시간·인원·버튼 순. 방 이름의 271px는 17px 볼드
 * 한글 15자(255px)에 칸 사이 여백 16px을 더한 값이고, 문제 이름만 남는 폭을 받는다.
 */
const roomRowColumnsClasses =
  'grid-cols-[auto_271px_minmax(0,1fr)_auto_auto_auto_auto_auto] ' +
  // 1024px 아래에서는 번호·방장·바퀴·시간·인원을 감추고 세 칸만 남긴다. 감춘 칸 수만큼
  // 열도 줄여야 한다 — 열이 남아 있으면 다음 행이 빈 열부터 이어져 격자가 어긋난다.
  'max-[1024px]:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_auto] ' +
  'max-[560px]:grid-cols-[minmax(0,1fr)_auto]';

/**
 * 행의 각 칸에 공통으로 걸리는 스타일. 칸 사이는 gap 대신 padding-right로 벌린다 —
 * gap을 쓰면 아래 구분선이 열 사이에서 끊겨 점선처럼 보인다.
 *
 * 칸은 행 높이까지 늘어나야(stretch) 아래 구분선이 한 줄로 이어진다. 격자에
 * items-center를 주면 칸마다 높이가 제 내용만큼이라 구분선이 층층이 어긋난다 —
 * 세로 가운데 정렬은 칸 안쪽에서 flex로 해결한다.
 */
const roomRowCellsClasses =
  'contents [&>*]:flex [&>*]:min-w-0 [&>*]:items-center [&>*]:border-b ' +
  '[&>*]:border-[#222] [&>*]:py-3 [&>*]:pr-4 ' +
  '[&>*:first-child]:pl-6 [&>*:last-child]:pr-6 last:[&>*]:border-b-0 ' +
  // 번호 칸이 숨어도 :first-child는 여전히 그 숨은 칸을 가리킨다 — 화면상 첫 칸이 되는
  // 방 이름에 왼쪽 여백을 다시 준다.
  'max-[1024px]:[&>*:nth-child(2)]:pl-6';

const LAP_CHOICES = [1, 2, 3] as const;
const SIZE_CHOICES = [2, 3, 4, 5, 6] as const;
/** 백엔드 RelayRoom.MIN/MAX_TURN_TIME_LIMIT_SECONDS(30~300) 안에서 고른 프리셋. */
const TURN_TIME_LIMIT_CHOICES = [30, 60, 90, 120, 180, 240, 300] as const;
/** 안 정하면 서버가 채우는 기본값과 같다(relay.turn-input-timeout, 2분). */
const DEFAULT_TURN_TIME_LIMIT_SECONDS = 120;
const ROOMS_PER_PAGE = 3;

/**
 * 방 이름 입력 제한. 백엔드 RelayRoom.MAX_NAME_LENGTH(30)보다 좁게 잡은 값으로,
 * 로비 목록 한 줄에 이름·문제·설정이 함께 들어가는 길이를 기준으로 정했다.
 */
const ROOM_NAME_MAX_LENGTH = 15;

/** 목록에 그대로 보여줄 길이. 제한이 15자로 좁아지기 전에 만들어진 방은 이보다 길 수 있다. */
const ROOM_NAME_DISPLAY_LENGTH = ROOM_NAME_MAX_LENGTH;

/** 긴 이름 하나가 목록 한 줄의 나머지 정보를 밀어내지 않게 자른다. */
function truncateRoomName(name: string): string {
  return name.length > ROOM_NAME_DISPLAY_LENGTH
    ? `${name.slice(0, ROOM_NAME_DISPLAY_LENGTH)}…`
    : name;
}

/** 로비가 스스로 목록을 다시 읽는 주기. 방 개설·입장은 수시로 일어나 손 새로고침만으론 낡는다. */
const ROOM_LIST_REFRESH_MS = 10_000;

/**
 * 릴레이 입구. 열린 방 목록에서 골라 들어가거나, 새 방을 만든다(문제·바퀴·정원 선택).
 *
 * 방 번호를 공유받아 주소로 직접 들어가는 것(/relay/rooms/N)도 여전히 동작한다 —
 * 목록은 발견 수단이지 입장의 유일한 관문이 아니다.
 */
export default function RelayLobbyPage() {
  const navigate = useNavigate();
  const { colorMode } = useTheme();

  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [roomName, setRoomName] = useState('');
  const [problemId, setProblemId] = useState<number | null>(null);
  const [totalLaps, setTotalLaps] = useState<number>(1);
  const [maxParticipants, setMaxParticipants] = useState<number>(3);
  const [turnTimeLimitSeconds, setTurnTimeLimitSeconds] = useState<number>(
    DEFAULT_TURN_TIME_LIMIT_SECONDS,
  );
  const [creating, setCreating] = useState(false);
  const [rooms, setRooms] = useState<RelayRoomSummary[]>([]);
  const [roomPage, setRoomPage] = useState(0);
  const [roomsLoaded, setRoomsLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const roomPageCount = Math.max(1, Math.ceil(rooms.length / ROOMS_PER_PAGE));
  const visibleRooms = rooms.slice(
    roomPage * ROOMS_PER_PAGE,
    (roomPage + 1) * ROOMS_PER_PAGE,
  );

  useEffect(() => {
    void getProblems()
      .then(({ problems: list }) => {
        setProblems(list);
        setProblemId((current) => current ?? list[0]?.id ?? null);
      })
      .catch(() => setError('문제 목록을 불러오지 못했습니다.'));
  }, []);

  const refreshRooms = useCallback(async (signal?: AbortSignal) => {
    try {
      const { rooms: list } = await getRelayRooms(signal);
      setRooms(list);
      setRoomsLoaded(true);
    } catch (cause) {
      // 목록은 주기적으로 다시 읽으므로 한 번의 실패를 화면 에러로 만들지 않는다.
      if (!isAbortError(cause)) setRoomsLoaded(true);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void refreshRooms(controller.signal);

    const timer = window.setInterval(() => void refreshRooms(), ROOM_LIST_REFRESH_MS);

    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [refreshRooms]);

  useEffect(() => {
    setRoomPage((current) => Math.min(current, roomPageCount - 1));
  }, [roomPageCount]);

  const handleCreate = async () => {
    const name = roomName.trim();
    if (!name || problemId === null || creating) return;

    setCreating(true);
    setError(null);
    try {
      const room = await createRelayRoom(
        name,
        problemId,
        totalLaps,
        maxParticipants,
        turnTimeLimitSeconds,
      );
      navigate(`/relay/rooms/${room.roomId}`);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : '방을 만들지 못했습니다.',
      );
      setCreating(false);
    }
  };

  return (
    <div
      className="relay-page min-h-dvh bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]"
      data-color-mode={colorMode}
    >
      <Header />

      <main className="mx-auto grid w-full max-w-[880px] gap-8 px-6 py-12">
        <div>
          <h1 className="page-title m-0 text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em] text-[var(--relay-acid)]">
            릴레이 모드
          </h1>
          <p className="mt-5 mb-0 text-[15px] leading-[1.7] text-[#a3a3a3]">
            릴레이 모드로 프롬프트를 연습하세요! 
            <br />
            여러 명이 하나의 문제에 대해 정해진 순서대로 프롬프트를 제출하고, 빌드/테스트 결과와 수정된 코드를 확인합니다.
            <br />
            각자 프롬프트 한 번씩 —
            앞사람이 만든 코드 위에서 다음 사람이 이어가며 문제를 해결해 보세요!
            <br />
          </p>
        </div>

        {error && (
          <p className="m-0 border border-[#5a2c28] bg-[#1c0f0e] px-4 py-3 text-xs text-[#ff786b]">
            {error}
          </p>
        )}

        <section className="border border-[#343434]">
          <div className="flex items-center justify-between border-b border-[#343434] px-6 py-3">
            <span className={labelClasses}>방 목록</span>
            <div className="flex items-center gap-2">
              <button
                aria-label="이전 방 목록 페이지"
                className="grid size-7 cursor-pointer place-items-center border border-[#3f3f3f] bg-transparent font-mono text-[12px] text-[#a3a3a3] enabled:hover:border-[#d6ff50] enabled:hover:text-[#d6ff50] disabled:cursor-not-allowed disabled:text-[#444]"
                disabled={roomPage === 0}
                onClick={() => setRoomPage((current) => current - 1)}
                type="button"
              >
                &lt;
              </button>
              <button
                aria-label="다음 방 목록 페이지"
                className="grid size-7 cursor-pointer place-items-center border border-[#3f3f3f] bg-transparent font-mono text-[12px] text-[#a3a3a3] enabled:hover:border-[#d6ff50] enabled:hover:text-[#d6ff50] disabled:cursor-not-allowed disabled:text-[#444]"
                disabled={roomPage >= roomPageCount - 1}
                onClick={() => setRoomPage((current) => current + 1)}
                type="button"
              >
                &gt;
              </button>
              <button
                className="cursor-pointer border border-[#3f3f3f] bg-transparent px-2.5 py-1 text-[10px] text-[#a3a3a3] hover:border-[#d6ff50] hover:text-[#d6ff50]"
                onClick={() => void refreshRooms()}
                type="button"
              >
                새로고침
              </button>
            </div>
          </div>

          {!roomsLoaded ? (
            <p className="m-0 px-4 py-6 text-[11px] text-[#a3a3a3]">
              방 목록을 불러오는 중…
            </p>
          ) : rooms.length === 0 ? (
            <p className="m-0 px-4 py-6 text-[11px] leading-[1.7] text-[#a3a3a3]">
              입장을 기다리는 방이 없습니다. 아래에서 새 방을 만들어 보세요.
            </p>
          ) : (
            <ul className={`m-0 grid list-none p-0 ${roomRowColumnsClasses}`}>
              {visibleRooms.map((room) => {
                const full = room.participantCount >= room.maxParticipants;

                return (
                  <li className={roomRowCellsClasses} key={room.roomId}>
                    {/* 좁아지면 방 이름·문제 이름·입장 버튼만 남긴다 — 나머지는 줄여
                        봐야 서로 침범할 뿐이고, 방을 고르는 데 꼭 필요하지도 않다. */}
                    <span className="font-mono text-[13px] text-[#a3a3a3] max-[1024px]:hidden!">
                      {String(room.roomId).padStart(2, '0')}
                    </span>
                    {/* 이름 도입 전에 만들어진 방은 name이 없어 문제 제목이 그 자리를 대신한다.
                        그때도 문제 이름 칸은 빈 칸으로 남겨 뒤 열의 위치를 지킨다. */}
                    {/* 칸이 flex 상자가 되어 줄임표는 안쪽 블록에 걸어야 먹는다. */}
                    <span
                      className="text-[17px] font-bold"
                      title={room.name ?? undefined}
                    >
                      <span className="min-w-0 flex-1 truncate">
                        {room.name
                          ? truncateRoomName(room.name)
                          : (room.problemTitle ?? `문제 ${room.problemId}번`)}
                      </span>
                    </span>
                    <span
                      className="text-[11px] text-[#a3a3a3] max-[560px]:hidden!"
                      title={room.problemTitle ?? undefined}
                    >
                      <span className="min-w-0 flex-1 truncate">
                        {room.name
                          ? (room.problemTitle ?? `문제 ${room.problemId}번`)
                          : ''}
                      </span>
                    </span>
                    <span className="text-[11px] whitespace-nowrap text-[#a3a3a3] max-[1024px]:hidden!">
                      {room.hostNickname} 님의 방
                    </span>
                    <span className="text-[11px] text-[#a3a3a3] max-[1024px]:hidden!">
                      {room.totalLaps}바퀴
                    </span>
                    <span className="font-mono text-[11px] text-[#a3a3a3] max-[1024px]:hidden!">
                      ⏱ {formatTurnTimeLimit(room.turnTimeLimitSeconds)}
                    </span>
                    <span
                      className={`font-mono text-[11px] font-bold max-[1024px]:hidden! ${
                        full ? 'text-[#ff786b]' : 'text-[#d6ff50]'
                      }`}
                    >
                      {room.participantCount}/{room.maxParticipants}
                    </span>
                    <span>
                      {full ? (
                      <span
                        className="inline-flex min-h-11 cursor-not-allowed items-center justify-center gap-2 border border-[#3f3f3f] bg-[#171717] px-[18px] text-[14px] leading-none font-extrabold tracking-[-0.01em] text-[#666]"
                        role="status"
                      >
                        <svg
                          aria-hidden="true"
                          className="size-4"
                          fill="none"
                          viewBox="0 0 24 24"
                        >
                          <rect height="10" rx="1" stroke="currentColor" strokeWidth="2" width="14" x="5" y="11" />
                          <path d="M8 11V8a4 4 0 0 1 8 0v3" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
                        </svg>
                        정원 마감
                      </span>
                    ) : (
                      <Button to={`/relay/rooms/${room.roomId}`}>
                        <svg
                          aria-hidden="true"
                          className="size-4"
                          fill="none"
                          viewBox="0 0 24 24"
                        >
                          <rect height="10" rx="1" stroke="currentColor" strokeWidth="2" width="14" x="5" y="11" />
                          <path d="M16 11V8a4 4 0 0 0-7.5-2" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
                        </svg>
                        입장 가능
                      </Button>
                      )}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        <section className="border border-[#343434] p-6">
          <div className={labelClasses}>방 만들기</div>

          <div className="mt-5 grid gap-4">
            <label className="grid gap-1.5">
              <span className="text-[12px] text-[#a3a3a3]">
                방 이름 — 로비 목록에 그대로 보입니다 (최대 {ROOM_NAME_MAX_LENGTH}자)
              </span>
              <input
                className={fieldClasses}
                maxLength={ROOM_NAME_MAX_LENGTH}
                onChange={(event) => setRoomName(event.target.value)}
                placeholder="예) 점심시간 한 판"
                type="text"
                value={roomName}
              />
            </label>

            <label className="grid gap-1.5">
              <span className="text-[12px] text-[#a3a3a3]">
                문제 — 함께 풀어볼 문제를 고르세요
              </span>
              <select
                className={selectClasses}
                onChange={(event) => setProblemId(Number(event.target.value))}
                style={selectArrowStyle}
                value={problemId ?? ''}
              >
                {problems.map((problem) => (
                  <option key={problem.id} value={problem.id}>
                    {problem.id}. {problem.title}
                  </option>
                ))}
              </select>
            </label>

            <div className="grid grid-cols-3 gap-4 max-[760px]:grid-cols-1">
              <label className="grid content-start gap-1.5">
                <span className="text-[12px] text-[#a3a3a3]">
                  최대 인원
                </span>
                <select
                  className={selectClasses}
                  onChange={(event) =>
                    setMaxParticipants(Number(event.target.value))
                  }
                  style={selectArrowStyle}
                  value={maxParticipants}
                >
                  {SIZE_CHOICES.map((size) => (
                    <option key={size} value={size}>
                      {size}명
                    </option>
                  ))}
                </select>
              </label>

              <label className="grid content-start gap-1.5">
                <span className="text-[12px] text-[#a3a3a3]">
                  바퀴 수 — 인원 × 바퀴 = 총 턴 수
                </span>
                <select
                  className={selectClasses}
                  onChange={(event) => setTotalLaps(Number(event.target.value))}
                  style={selectArrowStyle}
                  value={totalLaps}
                >
                  {LAP_CHOICES.map((laps) => (
                    <option key={laps} value={laps}>
                      {laps}바퀴
                    </option>
                  ))}
                </select>
              </label>

              <label className="grid content-start gap-1.5">
                <span className="text-[12px] text-[#a3a3a3]">
                  턴 제한시간
                </span>
                <select
                  className={selectClasses}
                  onChange={(event) =>
                    setTurnTimeLimitSeconds(Number(event.target.value))
                  }
                  style={selectArrowStyle}
                  value={turnTimeLimitSeconds}
                >
                  {TURN_TIME_LIMIT_CHOICES.map((seconds) => (
                    <option key={seconds} value={seconds}>
                      {formatTurnTimeLimit(seconds)}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <Button
              disabled={!roomName.trim() || problemId === null || creating}
              onClick={() => void handleCreate()}
            >
              {creating
                ? '만드는 중…'
                : roomName.trim()
                  ? '방 만들기'
                  : '방 이름을 입력하세요'}
            </Button>
          </div>
        </section>
      </main>
      <Footer />
    </div>
  );
}
