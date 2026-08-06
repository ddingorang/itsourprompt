import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { getProblems } from '../features/problem/api';
import type { ProblemSummary } from '../features/problem/types';
import { createRelayRoom, getRelayRooms } from '../features/relay/api';
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

const LAP_CHOICES = [1, 2, 3] as const;
const SIZE_CHOICES = [2, 3, 4, 5, 6] as const;
/** 백엔드 RelayRoom.MIN/MAX_TURN_TIME_LIMIT_SECONDS(30~300) 안에서 고른 프리셋. */
const TURN_TIME_LIMIT_CHOICES = [30, 60, 90, 120, 180, 240, 300] as const;
/** 안 정하면 서버가 채우는 기본값과 같다(relay.turn-input-timeout, 2분). */
const DEFAULT_TURN_TIME_LIMIT_SECONDS = 120;
const ROOMS_PER_PAGE = 3;

function formatTurnTimeLimit(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;

  if (minutes === 0) return `${remainder}초`;
  if (remainder === 0) return `${minutes}분`;

  return `${minutes}분 ${remainder}초`;
}

/** 백엔드 RelayRoom.MAX_NAME_LENGTH와 같은 값. 로비 목록 한 줄에 들어가는 길이. */
const ROOM_NAME_MAX_LENGTH = 30;

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
          <h1 className="m-0 text-[clamp(36px,6vw,64px)] leading-[0.82] font-bold tracking-[-0.04em] text-[#d6ff50]">
            릴레이 모드
          </h1>
          <p className="mt-2 mb-0 text-[13px] leading-[1.7] text-[#a3a3a3]">
            여러 명이 한 문제를 정해진 순서대로 이어 풉니다. 각자 프롬프트 한 번씩 —
            앞사람이 만든 코드 위에서 다음 사람이 이어갑니다.
            <br />
            턴이 끝날 때마다
            자동 채점되고, 직전 대비 통과 증가분이 그 사람의 기여도가 됩니다.
          </p>
        </div>

        {error && (
          <p className="m-0 border border-[#5a2c28] bg-[#1c0f0e] px-4 py-3 text-xs text-[#ff786b]">
            {error}
          </p>
        )}

        <section className="border border-[#343434]">
          <div className="flex items-center justify-between border-b border-[#343434] px-6 py-3">
            <span className={labelClasses}>열린 방</span>
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
            <p className="m-0 px-4 py-6 text-[11px] text-[#666]">
              방 목록을 불러오는 중…
            </p>
          ) : rooms.length === 0 ? (
            <p className="m-0 px-4 py-6 text-[11px] leading-[1.7] text-[#666]">
              입장을 기다리는 방이 없습니다. 아래에서 새 방을 만들어 보세요.
            </p>
          ) : (
            <ul className="m-0 grid list-none gap-0 p-0">
              {visibleRooms.map((room) => {
                const full = room.participantCount >= room.maxParticipants;

                return (
                  <li
                    className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-[#222] px-6 py-3 last:border-b-0"
                    key={room.roomId}
                  >
                    <span className="font-mono text-[13px] text-[#666]">
                      {String(room.roomId).padStart(2, '0')}
                    </span>
                    {/* 이름 도입 전에 만들어진 방은 name이 없다 — 문제 제목이 그 자리를 대신한다. */}
                    <span className="min-w-0 flex-1 text-[17px] font-bold">
                      {room.name ?? room.problemTitle ?? `문제 ${room.problemId}번`}
                    </span>
                    {room.name && (
                      <span className="text-[11px] text-[#a3a3a3]">
                        {room.problemTitle ?? `문제 ${room.problemId}번`}
                      </span>
                    )}
                    <span className="text-[11px] text-[#a3a3a3]">
                      {room.hostNickname} 님의 방
                    </span>
                    <span className="text-[11px] text-[#a3a3a3]">
                      {room.totalLaps}바퀴
                    </span>
                    <span className="font-mono text-[11px] text-[#a3a3a3]">
                      ⏱ {formatTurnTimeLimit(room.turnTimeLimitSeconds)}
                    </span>
                    <span
                      className={`font-mono text-[11px] font-bold ${
                        full ? 'text-[#ff786b]' : 'text-[#d6ff50]'
                      }`}
                    >
                      {room.participantCount}/{room.maxParticipants}
                    </span>
                    {full ? (
                      <span
                        className="inline-flex min-h-11 min-w-[128px] cursor-not-allowed items-center justify-center gap-2 border border-[#3f3f3f] bg-[#171717] px-[18px] text-[14px] leading-none font-extrabold tracking-[-0.01em] text-[#666]"
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
                      <Button
                        className="min-w-[128px]"
                        to={`/relay/rooms/${room.roomId}`}
                      >
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
              <span className="text-[12px] text-[#777]">
                방 이름 — 로비 목록에 그대로 보입니다
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
              <span className="text-[12px] text-[#777]">
                문제
              </span>
              <select
                className={fieldClasses}
                onChange={(event) => setProblemId(Number(event.target.value))}
                value={problemId ?? ''}
              >
                {problems.map((problem) => (
                  <option key={problem.id} value={problem.id}>
                    {problem.id}. {problem.title}
                  </option>
                ))}
              </select>
            </label>

            <div className="grid grid-cols-2 gap-4 max-[480px]:grid-cols-1">
              <label className="grid gap-1.5">
                <span className="text-[12px] text-[#777]">
                  바퀴 수 — 인원 × 바퀴 = 총 턴 수
                </span>
                <select
                  className={fieldClasses}
                  onChange={(event) => setTotalLaps(Number(event.target.value))}
                  value={totalLaps}
                >
                  {LAP_CHOICES.map((laps) => (
                    <option key={laps} value={laps}>
                      {laps}바퀴
                    </option>
                  ))}
                </select>
              </label>

              <label className="grid gap-1.5">
                <span className="text-[12px] text-[#777]">
                  최대 인원
                </span>
                <select
                  className={fieldClasses}
                  onChange={(event) =>
                    setMaxParticipants(Number(event.target.value))
                  }
                  value={maxParticipants}
                >
                  {SIZE_CHOICES.map((size) => (
                    <option key={size} value={size}>
                      {size}명
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <label className="grid gap-1.5">
              <span className="text-[12px] text-[#777]">
                턴 제한시간 — 주자 한 명의 입력 제한시간
              </span>
              <select
                className={fieldClasses}
                onChange={(event) =>
                  setTurnTimeLimitSeconds(Number(event.target.value))
                }
                value={turnTimeLimitSeconds}
              >
                {TURN_TIME_LIMIT_CHOICES.map((seconds) => (
                  <option key={seconds} value={seconds}>
                    {formatTurnTimeLimit(seconds)}
                  </option>
                ))}
              </select>
            </label>

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
