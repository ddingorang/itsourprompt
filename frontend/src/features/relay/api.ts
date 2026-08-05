import { apiRequest } from '../../shared/api/apiClient';
import type {
  IceServersResponse,
  RelayCode,
  RelayFeedback,
  RelayRoom,
  RelayRoomListResponse,
  RelayTurnListResponse,
  RelayTurnResponse,
} from './types';

/**
 * 릴레이 게임 API 호출 모음. 전부 로그인 필수다 — 릴레이는 게스트를 받지 않는다
 * (좌석의 주인이 세션 만료로 바뀌면 안 되기 때문).
 *
 * 어템프트 API와 달리 목(mock)이 없다. 릴레이는 여러 사람·WebSocket·워커가 얽힌
 * 흐름이라 목으로 흉내 내면 실제와 다른 화면을 검증하게 된다.
 *
 * 턴 전송에 Idempotency-Key를 붙이지 않는 것은 의도다 — 중복 전송은 서버의
 * 좌석 단위 원자적 전이(조건부 UPDATE)가 409로 막아 준다.
 */

export async function createRelayRoom(
  name: string,
  problemId: number,
  totalLaps: number,
  maxParticipants: number,
): Promise<RelayRoom> {
  return apiRequest<RelayRoom>('/relay/rooms', {
    body: JSON.stringify({ maxParticipants, name, problemId, totalLaps }),
    method: 'POST',
  });
}

/** 입장 가능한 방 목록(WAITING + 사람 있음), 최신 개설 순. 로비가 쓴다. */
export async function getRelayRooms(
  signal?: AbortSignal,
): Promise<RelayRoomListResponse> {
  return apiRequest<RelayRoomListResponse>('/relay/rooms', { signal });
}

/** 방 상태 스냅샷. 재접속 복구가 이 응답 하나로 가능해야 한다(서버가 그렇게 설계됨). */
export async function getRelayRoom(
  roomId: number,
  signal?: AbortSignal,
): Promise<RelayRoom> {
  return apiRequest<RelayRoom>(`/relay/rooms/${roomId}`, { signal });
}

/** 입장. 이미 참가자면 서버가 중복 등록 없이 현재 상태를 돌려준다(멱등). */
export async function joinRelayRoom(roomId: number): Promise<RelayRoom> {
  return apiRequest<RelayRoom>(`/relay/rooms/${roomId}/participants`, {
    method: 'POST',
  });
}

export async function leaveRelayRoom(roomId: number): Promise<RelayRoom> {
  return apiRequest<RelayRoom>(`/relay/rooms/${roomId}/participants/me`, {
    method: 'DELETE',
  });
}

/** 게임 시작(방장만). 입장 순서대로 좌석이 확정된다. */
export async function startRelayGame(roomId: number): Promise<RelayRoom> {
  return apiRequest<RelayRoom>(`/relay/rooms/${roomId}/start`, {
    method: 'POST',
  });
}

/**
 * 턴 전송(현재 좌석만). AI 생성이 끝날 때까지 블로킹된다(수십 초).
 * 대기자들은 같은 내용을 WebSocket 이벤트로 받으므로, 이 응답은 주자 본인용이다.
 */
export async function submitRelayTurn(
  roomId: number,
  prompt: string,
): Promise<RelayTurnResponse> {
  return apiRequest<RelayTurnResponse>(`/relay/rooms/${roomId}/turns`, {
    body: JSON.stringify({ prompt }),
    method: 'POST',
  });
}

/** 현재 코드(참가자만). 다음 주자는 이 코드를 보고 프롬프트를 궁리한다. */
export async function getRelayCode(
  roomId: number,
  signal?: AbortSignal,
): Promise<RelayCode> {
  return apiRequest<RelayCode>(`/relay/rooms/${roomId}/code`, { signal });
}

/** 턴 이력과 점수(참가자만). 놓친 grading.finished 이벤트 대신 스코어보드를 복원한다. */
export async function getRelayTurns(
  roomId: number,
  signal?: AbortSignal,
): Promise<RelayTurnListResponse> {
  return apiRequest<RelayTurnListResponse>(`/relay/rooms/${roomId}/turns`, {
    signal,
  });
}

/** 게임 피드백(참가자만). 생성 전에는 404(feedback-not-found)로 실패한다. */
export async function getRelayFeedback(
  roomId: number,
  signal?: AbortSignal,
): Promise<RelayFeedback> {
  return apiRequest<RelayFeedback>(`/relay/rooms/${roomId}/feedback`, {
    signal,
  });
}

/** 실패한 피드백 생성 재시도(참가자 누구나). 202 접수 후 결과는 소켓으로 온다. */
export async function retryRelayFeedback(roomId: number): Promise<void> {
  return apiRequest<void>(`/relay/rooms/${roomId}/feedback/retry`, {
    method: 'POST',
  });
}

/** ICE 서버 설정. 응답을 그대로 RTCPeerConnection 생성자에 넣는다. */
export async function getIceServers(
  signal?: AbortSignal,
): Promise<IceServersResponse> {
  return apiRequest<IceServersResponse>('/relay/ice-servers', { signal });
}
