import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { getProblems } from '../features/problem/api';
import type { ProblemSummary } from '../features/problem/types';
import { createRelayRoom, getRelayRooms } from '../features/relay/api';
import type { RelayRoomSummary } from '../features/relay/types';
import { ApiError, isAbortError } from '../shared/api/apiClient';
import Button from '../shared/components/Button';
import Header from '../shared/components/Header';

const labelClasses =
  'font-mono text-sm leading-[1.5] font-bold tracking-[0.08em] text-[#d6ff50]';

const fieldClasses =
  'w-full border border-[#3f3f3f] bg-[#151515] px-3 py-2.5 font-mono text-sm ' +
  'text-[#f5f5ef] focus:border-[#d6ff50] focus:outline-none';

const LAP_CHOICES = [1, 2, 3] as const;
const SIZE_CHOICES = [2, 3, 4, 5, 6] as const;

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

  const [problems, setProblems] = useState<ProblemSummary[]>([]);
  const [roomName, setRoomName] = useState('');
  const [problemId, setProblemId] = useState<number | null>(null);
  const [totalLaps, setTotalLaps] = useState<number>(1);
  const [maxParticipants, setMaxParticipants] = useState<number>(3);
  const [creating, setCreating] = useState(false);
  const [rooms, setRooms] = useState<RelayRoomSummary[]>([]);
  const [roomsLoaded, setRoomsLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

  const handleCreate = async () => {
    const name = roomName.trim();
    if (!name || problemId === null || creating) return;

    setCreating(true);
    setError(null);
    try {
      const room = await createRelayRoom(name, problemId, totalLaps, maxParticipants);
      navigate(`/relay/rooms/${room.roomId}`);
    } catch (cause) {
      setError(
        cause instanceof ApiError ? cause.message : '방을 만들지 못했습니다.',
      );
      setCreating(false);
    }
  };

  return (
    <div className="min-h-dvh bg-[#090909] text-[#f5f5ef] [font-family:Arial,'Noto_Sans_KR',sans-serif]">
      <Header />

      <main className="mx-auto grid w-full max-w-[880px] gap-8 px-6 py-12">
        <div>
          <h1 className="m-0 text-[28px] font-black tracking-[-0.03em]">
            RELAY<i className="not-italic text-[#d6ff50]">.</i>MODE
          </h1>
          <p className="mt-2 mb-0 text-[13px] leading-[1.7] text-[#a3a3a3]">
            여러 명이 한 문제를 정해진 순서대로 이어 풉니다. 각자 프롬프트 한 번씩 —
            앞사람이 만든 코드 위에서 다음 사람이 이어갑니다. 턴이 끝날 때마다
            자동 채점되고, 직전 대비 통과 증가분이 그 사람의 기여도가 됩니다.
          </p>
        </div>

        {error && (
          <p className="m-0 border border-[#5a2c28] bg-[#1c0f0e] px-4 py-3 font-mono text-xs text-[#ff786b]">
            {error}
          </p>
        )}

        <section className="border border-[#343434]">
          <div className="flex items-center justify-between border-b border-[#343434] px-4 py-3">
            <span className={labelClasses}>OPEN ROOMS</span>
            <button
              className="cursor-pointer border border-[#3f3f3f] bg-transparent px-2.5 py-1 font-mono text-[10px] text-[#a3a3a3] hover:border-[#d6ff50] hover:text-[#d6ff50]"
              onClick={() => void refreshRooms()}
              type="button"
            >
              새로고침
            </button>
          </div>

          {!roomsLoaded ? (
            <p className="m-0 px-4 py-6 font-mono text-[11px] text-[#666]">
              방 목록을 불러오는 중…
            </p>
          ) : rooms.length === 0 ? (
            <p className="m-0 px-4 py-6 font-mono text-[11px] leading-[1.7] text-[#666]">
              입장을 기다리는 방이 없습니다. 아래에서 새 방을 만들어 보세요.
            </p>
          ) : (
            <ul className="m-0 grid list-none gap-0 p-0">
              {rooms.map((room) => {
                const full = room.participantCount >= room.maxParticipants;

                return (
                  <li
                    className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-[#222] px-4 py-3 last:border-b-0"
                    key={room.roomId}
                  >
                    <span className="font-mono text-[11px] text-[#666]">
                      #{room.roomId}
                    </span>
                    {/* 이름 도입 전에 만들어진 방은 name이 없다 — 문제 제목이 그 자리를 대신한다. */}
                    <span className="min-w-0 flex-1 text-[13px] font-bold">
                      {room.name ?? room.problemTitle ?? `문제 ${room.problemId}번`}
                    </span>
                    {room.name && (
                      <span className="font-mono text-[11px] text-[#a3a3a3]">
                        {room.problemTitle ?? `문제 ${room.problemId}번`}
                      </span>
                    )}
                    <span className="font-mono text-[11px] text-[#a3a3a3]">
                      {room.hostNickname} 님의 방
                    </span>
                    <span className="font-mono text-[11px] text-[#a3a3a3]">
                      {room.totalLaps}바퀴
                    </span>
                    <span
                      className={`font-mono text-[11px] font-bold ${
                        full ? 'text-[#ff786b]' : 'text-[#d6ff50]'
                      }`}
                    >
                      {room.participantCount}/{room.maxParticipants}
                    </span>
                    <Button
                      disabled={full}
                      to={`/relay/rooms/${room.roomId}`}
                      variant="secondary"
                    >
                      {full ? '만원' : '입장'}
                    </Button>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        <section className="border border-[#343434] p-6">
          <div className={labelClasses}>CREATE ROOM</div>

          <div className="mt-5 grid gap-4">
            <label className="grid gap-1.5">
              <span className="font-mono text-[10px] tracking-[0.12em] text-[#777]">
                ROOM NAME — 로비 목록에 그대로 보입니다
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
              <span className="font-mono text-[10px] tracking-[0.12em] text-[#777]">
                PROBLEM
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
                <span className="font-mono text-[10px] tracking-[0.12em] text-[#777]">
                  LAPS — 인원 × 바퀴 = 총 턴 수
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
                <span className="font-mono text-[10px] tracking-[0.12em] text-[#777]">
                  MAX PLAYERS
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
    </div>
  );
}
