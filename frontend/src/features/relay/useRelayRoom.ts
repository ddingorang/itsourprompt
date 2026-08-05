import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type RefObject,
} from 'react';

import { ApiError } from '../../shared/api/apiClient';
import {
  getRelayCode,
  getRelayTurns,
  joinRelayRoom,
  submitRelayTurn,
} from './api';
import type {
  RelayCode,
  RelayEvent,
  RelayGradingOutcome,
  RelayRoom,
  RelaySignalType,
  RelayTurnRecord,
  RelayTurnSummary,
} from './types';

/**
 * 소켓 연결 상태. 게임 상태(room.status)와 별개다 — 소켓이 끊겨도 게임은 서버에서
 * 계속 진행되고, 재접속하면 스냅샷 한 번으로 따라잡는다.
 *
 *   replaced    같은 계정이 다른 탭에서 접속해 이 탭이 밀려났다. 재연결하지 않는다 —
 *               서로 밀어내기를 반복하는 탭 싸움이 된다
 *   rejected    참가자가 아니거나 방이 없다. 재연결해도 같은 결과다
 */
export type RelaySocketStatus =
  | 'connecting'
  | 'open'
  | 'closed'
  | 'replaced'
  | 'rejected';

export interface RelayRoomState {
  room: RelayRoom | null;
  /** 입장 자체가 거부된 경우(정원 초과 등). 방 화면 대신 안내를 그린다. */
  joinError: string | null;
  socketStatus: RelaySocketStatus;
  code: RelayCode | null;
  turns: RelayTurnRecord[];
  /** 가장 최근에 끝난 턴의 요약. 대기자 화면의 "방금 무슨 일이 있었나". */
  lastTurn: RelayTurnSummary | null;
  lastGrading: RelayGradingOutcome | null;
  /** 가장 최근에 건너뛴 턴(이탈·입력 마감 초과). "왜 갑자기 다음 차례지"가 되지 않게 알린다. */
  lastSkip: { turnIndex: number; authorUserId: number } | null;
  feedbackFailed: boolean;
  /** 내 턴 전송이 진행 중인지(요청 → 응답 사이). */
  submitting: boolean;
  /** 내 턴 전송이 실패했을 때의 메시지. 다음 전송 시도에서 지워진다. */
  submitError: string | null;
}

const RECONNECT_BASE_DELAY_MS = 1000;
const RECONNECT_MAX_DELAY_MS = 15_000;

/**
 * 백엔드 WebSocket 주소. 개발은 vite 프록시(/ws)를 타고, VITE_API_BASE_URL로 다른
 * 오리진을 가리키는 배포에서는 그 오리진의 ws(s)로 붙는다.
 */
function relaySocketUrl(roomId: number): string {
  const apiBase = import.meta.env.VITE_API_BASE_URL as string | undefined;

  if (apiBase && /^https?:\/\//.test(apiBase)) {
    const origin = new URL(apiBase);
    const protocol = origin.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${origin.host}/ws/relay/${roomId}`;
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws/relay/${roomId}`;
}

/**
 * 릴레이 방 하나에 대한 서버 권위 상태.
 *
 * 마운트 시 입장(멱등 — 재접속이면 서버가 중복 등록 없이 현재 상태를 돌려준다)하고
 * 소켓을 연결한다. 접속 직후 서버가 room.state 스냅샷을 보내므로 별도 조회가 없고,
 * 화면 상태는 항상 room.state로만 갱신한다 — 다른 이벤트(turn.finished 등)는 부가
 * 정보이고, 상태 전이는 뒤따르는 room.state가 확정한다.
 *
 * peer.*·signal.* 이벤트는 게임 상태가 아니라 WebRTC 몫이라 rtcHandlerRef로
 * 넘긴다. ref인 이유: RTC 훅은 시그널 전송 함수(sendSignal)를 필요로 해서 이 훅
 * 다음에 만들어지는데, 이벤트 구독은 이 훅이 하므로 나중에 채울 자리가 필요하다.
 */
export function useRelayRoom(
  roomId: number,
  rtcHandlerRef: RefObject<(event: RelayEvent) => void>,
) {
  const [state, setState] = useState<RelayRoomState>({
    code: null,
    feedbackFailed: false,
    joinError: null,
    lastGrading: null,
    lastSkip: null,
    lastTurn: null,
    room: null,
    socketStatus: 'connecting',
    submitError: null,
    submitting: false,
    turns: [],
  });

  const socketRef = useRef<WebSocket | null>(null);
  // 코드가 이미 실려 있는지 이벤트 핸들러에서 보는 거울. state를 직접 읽으면
  // 콜백이 옛 스냅샷에 갇힌다.
  const codeLoadedRef = useRef(false);

  const refreshCode = useCallback(async () => {
    try {
      const code = await getRelayCode(roomId);
      codeLoadedRef.current = true;
      setState((prev) => ({ ...prev, code }));
    } catch {
      // 게임 시작 전(409)이거나 일시 실패다. room.state·turn.finished가 다시 시도한다.
    }
  }, [roomId]);

  const refreshTurns = useCallback(async () => {
    try {
      const { turns } = await getRelayTurns(roomId);
      setState((prev) => ({ ...prev, turns }));
    } catch {
      // 스코어보드도 다음 채점 이벤트에서 다시 동기화된다.
    }
  }, [roomId]);

  const handleEvent = useCallback(
    (event: RelayEvent) => {
      switch (event.type) {
        case 'room.state':
          setState((prev) => ({ ...prev, room: event.payload }));
          // 게임이 시작된 방인데 코드가 아직 없다 — 시작 전(WAITING)에 열린 소켓은
          // 첫 조회가 409로 끝나므로, 시작 전이가 온 지금이 스켈레톤을 실을 순간이다.
          // 실패해도 다음 room.state가 다시 시도해 준다.
          if (event.payload.status !== 'WAITING' && !codeLoadedRef.current) {
            void refreshCode();
          }
          break;
        case 'turn.finished':
          setState((prev) => ({ ...prev, lastTurn: event.payload }));
          void refreshCode();
          break;
        case 'grading.finished':
          setState((prev) => ({ ...prev, lastGrading: event.payload }));
          void refreshTurns();
          break;
        case 'turn.skipped':
          setState((prev) => ({ ...prev, lastSkip: event.payload }));
          // 스킵된 턴도 이력에 행이 생긴다 — 스코어보드를 따라잡는다.
          void refreshTurns();
          break;
        case 'feedback.ready':
          setState((prev) => ({ ...prev, feedbackFailed: false }));
          break;
        case 'feedback.failed':
          setState((prev) => ({ ...prev, feedbackFailed: true }));
          break;
        case 'turn.failed':
        case 'grading.started':
          // 상태 전이는 함께 오는 room.state가 처리한다. 별도 화면 갱신이 없다.
          break;
        default:
          rtcHandlerRef.current(event);
      }
    },
    [refreshCode, refreshTurns, rtcHandlerRef],
  );

  const handleEventRef = useRef(handleEvent);
  useEffect(() => {
    handleEventRef.current = handleEvent;
  }, [handleEvent]);

  useEffect(() => {
    // 폐기 표지는 effect 실행마다 새로 만든다(지역 변수). ref로 공유하면 StrictMode의
    // 마운트→정리→재마운트에서 2차 실행이 표지를 되돌려, 1차 실행의 비동기 입장 응답이
    // "아직 살아 있다"고 착각하고 소켓을 하나 더 연다 — 같은 계정의 소켓 두 개가 되어
    // 서버가 첫 소켓을 session-replaced로 끊고, 화면은 차단 안내에 갇힌다.
    let disposed = false;
    let socket: WebSocket | null = null;
    let reconnectTimer: number | null = null;
    let reconnectAttempt = 0;

    const connect = () => {
      if (disposed) return;

      setState((prev) => ({ ...prev, socketStatus: 'connecting' }));
      const current = new WebSocket(relaySocketUrl(roomId));
      socket = current;
      socketRef.current = current;

      // 모든 핸들러가 "내가 아직 현재 소켓인가"를 확인한다. 재연결·이중 마운트로
      // 대체된 낡은 소켓의 종료를 현재 연결의 종료로 오인하지 않기 위해서다.
      const stale = () => disposed || socketRef.current !== current;

      current.onopen = () => {
        if (stale()) return;
        reconnectAttempt = 0;
        setState((prev) => ({ ...prev, socketStatus: 'open' }));
        // 끊겨 있는 동안의 채점을 따라잡는다. 방 상태는 서버가 접속 직후
        // room.state로 보내 주지만 턴 이력 브로드캐스트는 다시 오지 않는다.
        void refreshTurns();
        void refreshCode();
      };

      current.onmessage = (message: MessageEvent<string>) => {
        if (stale()) return;
        handleEventRef.current(JSON.parse(message.data) as RelayEvent);
      };

      current.onclose = (event) => {
        if (stale()) return;

        // 같은 계정의 다른 탭에 밀려났다. 재연결하면 서로 밀어내기를 반복한다.
        if (event.reason === 'session-replaced') {
          setState((prev) => ({ ...prev, socketStatus: 'replaced' }));
          return;
        }

        // 1008(비참가자)·1003(없는 방)은 다시 붙어도 같은 결과다.
        if (event.code === 1008 || event.code === 1003) {
          setState((prev) => ({ ...prev, socketStatus: 'rejected' }));
          return;
        }

        setState((prev) => ({ ...prev, socketStatus: 'closed' }));
        const delay = Math.min(
          RECONNECT_BASE_DELAY_MS * 2 ** reconnectAttempt,
          RECONNECT_MAX_DELAY_MS,
        );
        reconnectAttempt += 1;
        reconnectTimer = window.setTimeout(connect, delay);
      };
    };

    // 입장은 멱등이라 새 입장과 재접속을 구분할 필요가 없다.
    void (async () => {
      try {
        const room = await joinRelayRoom(roomId);
        if (disposed) return;
        setState((prev) => ({ ...prev, room }));
        connect();
      } catch (error) {
        if (disposed) return;
        const message =
          error instanceof ApiError
            ? error.message
            : '방에 입장하지 못했습니다.';
        setState((prev) => ({ ...prev, joinError: message }));
      }
    })();

    return () => {
      disposed = true;
      if (reconnectTimer !== null) window.clearTimeout(reconnectTimer);
      socket?.close();
      // 다음 effect 실행이 이미 새 소켓을 걸어 뒀을 수 있다 — 내 것일 때만 비운다.
      if (socketRef.current === socket) {
        socketRef.current = null;
      }
    };
  }, [roomId, refreshCode, refreshTurns]);

  /** WebRTC 시그널 전송. 서버는 payload를 해석하지 않고 대상에게 중계만 한다. */
  const sendSignal = useCallback(
    (type: RelaySignalType, targetUserId: number, payload: unknown) => {
      const socket = socketRef.current;
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ payload, targetUserId, type }));
      }
    },
    [],
  );

  /**
   * 내 턴 전송. 응답의 room은 채점 요청 직후 상태지만 그대로 반영하지 않는다 —
   * 소켓의 room.state가 같은 내용을 (순서 보장과 함께) 이미 나르고 있고,
   * 두 경로가 상태를 쓰면 늦게 도착한 쪽이 최신을 덮는다.
   */
  const submitTurn = useCallback(
    async (prompt: string) => {
      setState((prev) => ({ ...prev, submitError: null, submitting: true }));
      try {
        await submitRelayTurn(roomId, prompt);
      } catch (error) {
        const message =
          error instanceof ApiError
            ? error.message
            : '턴 전송에 실패했습니다. 다시 시도해 주세요.';
        setState((prev) => ({ ...prev, submitError: message }));
      } finally {
        setState((prev) => ({ ...prev, submitting: false }));
      }
    },
    [roomId],
  );

  return { ...state, sendSignal, submitTurn };
}
