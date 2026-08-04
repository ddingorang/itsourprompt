import { useCallback, useEffect, useRef, useState } from 'react';

import { getIceServers } from './api';
import type {
  RelayDataMessage,
  RelayEvent,
  RelaySignalType,
} from './types';

/**
 * WebRTC mesh. 게임 상태와 완전히 분리된 "사람 레이어"다 — 여기서 오가는 것
 * (음성, 타이핑 미리보기, 리액션)은 잃어도 게임 진행과 무관하고, 서버를 지나지 않는다.
 *
 * 접속 규약(백엔드 docs/relay-webrtc-manual-check.md와 동일):
 * - peer.list를 받은 쪽(새로 접속한 나)이 initiator — 목록 전원에게 연결을 만든다
 * - peer.joined를 받은 쪽은 그 피어의 offer를 기다린다
 * - offer 충돌(glare)은 perfect negotiation으로 푼다: polite(userId가 큰 쪽)가 양보
 */

export interface RelayPeerView {
  userId: number;
  connectionState: RTCPeerConnectionState;
  /** 이 피어가 타이핑 중인 프롬프트 미리보기. */
  typing: string;
  /** 최근 리액션. 표시 후 사라진다. */
  reaction: string | null;
  /** 이 피어의 음성 스트림. 오디오를 켠 피어만 값이 있다. */
  stream: MediaStream | null;
}

interface PeerEntry {
  connection: RTCPeerConnection;
  channel: RTCDataChannel | null;
  makingOffer: boolean;
}

const REACTION_VISIBLE_MS = 3000;

export function useRelayRtc(
  myUserId: number | null,
  sendSignal: (type: RelaySignalType, targetUserId: number, payload: unknown) => void,
) {
  const [peers, setPeers] = useState<Map<number, RelayPeerView>>(new Map());
  const [audioOn, setAudioOn] = useState(false);
  const [audioError, setAudioError] = useState<string | null>(null);

  const entriesRef = useRef<Map<number, PeerEntry>>(new Map());
  const iceServersRef = useRef<RTCIceServer[]>([]);
  const localStreamRef = useRef<MediaStream | null>(null);
  const myIdRef = useRef<number | null>(myUserId);
  const reactionTimersRef = useRef<Map<number, number>>(new Map());

  useEffect(() => {
    myIdRef.current = myUserId;
  }, [myUserId]);

  useEffect(() => {
    // ICE 설정은 게임 내내 같으므로 한 번만 받는다. 실패해도 빈 목록으로 진행한다 —
    // 같은 네트워크(host 후보)면 STUN 없이도 붙는다.
    void getIceServers()
      .then(({ iceServers }) => {
        iceServersRef.current = iceServers.map((server) =>
          server.username
            ? {
                credential: server.credential ?? undefined,
                urls: server.urls,
                username: server.username,
              }
            : { urls: server.urls },
        );
      })
      .catch(() => {
        iceServersRef.current = [];
      });

    const entries = entriesRef.current;
    const timers = reactionTimersRef.current;

    return () => {
      entries.forEach((entry) => entry.connection.close());
      entries.clear();
      timers.forEach((timer) => window.clearTimeout(timer));
      localStreamRef.current?.getTracks().forEach((track) => track.stop());
    };
  }, []);

  const patchPeer = useCallback(
    (userId: number, patch: Partial<RelayPeerView>) => {
      setPeers((prev) => {
        const next = new Map(prev);
        const current = next.get(userId) ?? {
          connectionState: 'new' as RTCPeerConnectionState,
          reaction: null,
          stream: null,
          typing: '',
          userId,
        };
        next.set(userId, { ...current, ...patch });
        return next;
      });
    },
    [],
  );

  const dropPeer = useCallback((userId: number) => {
    entriesRef.current.get(userId)?.connection.close();
    entriesRef.current.delete(userId);
    setPeers((prev) => {
      const next = new Map(prev);
      next.delete(userId);
      return next;
    });
  }, []);

  const wireChannel = useCallback(
    (userId: number, channel: RTCDataChannel) => {
      const entry = entriesRef.current.get(userId);
      if (entry) entry.channel = channel;

      channel.onmessage = (message: MessageEvent<string>) => {
        const data = JSON.parse(message.data) as RelayDataMessage;

        if (data.kind === 'typing') {
          patchPeer(userId, { typing: data.text });
          return;
        }

        patchPeer(userId, { reaction: data.emoji });
        const previous = reactionTimersRef.current.get(userId);
        if (previous !== undefined) window.clearTimeout(previous);
        reactionTimersRef.current.set(
          userId,
          window.setTimeout(() => patchPeer(userId, { reaction: null }), REACTION_VISIBLE_MS),
        );
      };
    },
    [patchPeer],
  );

  const ensurePeer = useCallback(
    (userId: number, initiator: boolean): PeerEntry => {
      const existing = entriesRef.current.get(userId);
      if (existing) return existing;

      const connection = new RTCPeerConnection({ iceServers: iceServersRef.current });
      const entry: PeerEntry = { channel: null, connection, makingOffer: false };
      entriesRef.current.set(userId, entry);
      patchPeer(userId, { connectionState: 'new' });

      connection.onicecandidate = ({ candidate }) => {
        if (candidate) sendSignal('signal.ice', userId, { candidate });
      };

      connection.onconnectionstatechange = () => {
        patchPeer(userId, { connectionState: connection.connectionState });
      };

      connection.onnegotiationneeded = () => {
        void (async () => {
          try {
            entry.makingOffer = true;
            await connection.setLocalDescription();
            sendSignal('signal.offer', userId, {
              description: connection.localDescription,
            });
          } finally {
            entry.makingOffer = false;
          }
        })();
      };

      connection.ondatachannel = ({ channel }) => wireChannel(userId, channel);
      connection.ontrack = ({ streams }) => {
        patchPeer(userId, { stream: streams[0] ?? null });
      };

      if (initiator) {
        // 채널 생성이 negotiationneeded를 깨워 offer가 나간다.
        wireChannel(userId, connection.createDataChannel('relay'));
      }

      // 내가 이미 오디오를 켠 상태에서 새 피어가 오면 그 연결에도 트랙을 싣는다.
      const localStream = localStreamRef.current;
      if (localStream) {
        localStream.getTracks().forEach((track) => {
          connection.addTrack(track, localStream);
        });
      }

      return entry;
    },
    [patchPeer, sendSignal, wireChannel],
  );

  const handleSignal = useCallback(
    async (
      type: 'signal.offer' | 'signal.answer' | 'signal.ice',
      fromUserId: number,
      data: unknown,
    ) => {
      const entry = ensurePeer(fromUserId, false);
      const { connection } = entry;
      const payload = data as {
        description?: RTCSessionDescriptionInit;
        candidate?: RTCIceCandidateInit;
      };

      if (type === 'signal.ice') {
        if (payload.candidate) {
          try {
            await connection.addIceCandidate(payload.candidate);
          } catch {
            // 무시된 offer(충돌) 뒤에 도착한 후보다. 재협상 offer가 다시 해결한다.
          }
        }
        return;
      }

      const description = payload.description;
      if (!description) return;

      if (description.type === 'offer') {
        // perfect negotiation: 충돌이면 polite(userId가 큰 쪽)가 자기 offer를 버린다.
        const polite = (myIdRef.current ?? 0) > fromUserId;
        const collision = entry.makingOffer || connection.signalingState !== 'stable';
        if (collision && !polite) return;

        await connection.setRemoteDescription(description);
        await connection.setLocalDescription();
        sendSignal('signal.answer', fromUserId, {
          description: connection.localDescription,
        });
        return;
      }

      await connection.setRemoteDescription(description);
    },
    [ensurePeer, sendSignal],
  );

  /** useRelayRoom이 peer.*·signal.* 이벤트를 이 함수로 넘긴다. */
  const handleEvent = useCallback(
    (event: RelayEvent) => {
      switch (event.type) {
        case 'peer.list':
          // 새로 온 내가 initiator — 이미 접속 중인 전원에게 연결을 만든다.
          event.payload.userIds.forEach((userId) => ensurePeer(userId, true));
          break;
        case 'peer.joined':
          // 새로 온 쪽이 offer를 만들므로 나는 기다린다. 자리도 offer가 만든다.
          break;
        case 'peer.left':
          dropPeer(event.payload.userId);
          break;
        case 'signal.offer':
        case 'signal.answer':
        case 'signal.ice':
          void handleSignal(event.type, event.payload.fromUserId, event.payload.data);
          break;
        default:
          break;
      }
    },
    [dropPeer, ensurePeer, handleSignal],
  );

  const broadcastData = useCallback((message: RelayDataMessage) => {
    const encoded = JSON.stringify(message);
    entriesRef.current.forEach((entry) => {
      if (entry.channel?.readyState === 'open') entry.channel.send(encoded);
    });
  }, []);

  /** 타이핑 미리보기. 초당 수십 번 불려도 서버 부하가 없다 — P2P라서 이 채널에 실었다. */
  const sendTyping = useCallback(
    (text: string) => broadcastData({ kind: 'typing', text }),
    [broadcastData],
  );

  const sendReaction = useCallback(
    (emoji: string) => broadcastData({ emoji, kind: 'reaction' }),
    [broadcastData],
  );

  const toggleAudio = useCallback(async () => {
    if (localStreamRef.current) {
      localStreamRef.current.getTracks().forEach((track) => track.stop());
      localStreamRef.current = null;
      setAudioOn(false);
      // 트랙 제거로 재협상을 걸기보다 연결은 유지한다 — 끈 마이크는 무음 트랙일 뿐이다.
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      localStreamRef.current = stream;
      setAudioError(null);
      setAudioOn(true);
      entriesRef.current.forEach((entry) => {
        stream.getTracks().forEach((track) => {
          entry.connection.addTrack(track, stream); // 재협상 유발
        });
      });
    } catch {
      setAudioError('마이크를 사용할 수 없습니다. 브라우저 권한을 확인해 주세요.');
    }
  }, []);

  return {
    audioError,
    audioOn,
    handleEvent,
    peers,
    sendReaction,
    sendTyping,
    toggleAudio,
  };
}
