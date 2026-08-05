package com.promptstudio.relay.service;

import com.promptstudio.relay.domain.RelayParticipant;
import com.promptstudio.relay.domain.RelayRoom;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.repository.RelayParticipantRepository;
import com.promptstudio.user.domain.User;
import com.promptstudio.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 방 스냅샷 조립. 로비 서비스와 게임 진행 서비스가 같은 스냅샷을 내보내야 하므로 한곳에 둔다.
 */
@Component
public class RelayRoomViewFactory {

    private final RelayParticipantRepository participantRepository;
    private final UserRepository userRepository;

    public RelayRoomViewFactory(
            RelayParticipantRepository participantRepository,
            UserRepository userRepository
    ) {
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
    }

    public RelayRoomView toView(RelayRoom room) {
        return toView(room, participantRepository.findByRoomIdOrderByJoinedAt(room.id()));
    }

    /**
     * 참가자 목록을 이미 들고 있는 호출자용. 변경 직후에는 다시 조회하면 안 되는 경우가 있다 —
     * 삭제가 플러시되기 전에 읽으면 나간 사람이 그대로 실린다.
     */
    public RelayRoomView toView(RelayRoom room, List<RelayParticipant> participants) {
        return RelayRoomView.of(room, toParticipantViews(participants));
    }

    private List<RelayRoomView.RelayParticipantView> toParticipantViews(List<RelayParticipant> participants) {
        Map<Long, String> nicknames = nicknamesOf(participants);
        List<RelayRoomView.RelayParticipantView> views = new ArrayList<>();

        for (RelayParticipant participant : participants) {
            views.add(new RelayRoomView.RelayParticipantView(
                    participant.userId(),
                    nicknames.get(participant.userId()),
                    participant.seatOrder(),
                    participant.joinedAt(),
                    participant.hasLeft()
            ));
        }

        return views;
    }

    private Map<Long, String> nicknamesOf(List<RelayParticipant> participants) {
        if (participants.isEmpty()) {
            return Map.of();
        }

        List<Long> userIds = new ArrayList<>();

        for (RelayParticipant participant : participants) {
            userIds.add(participant.userId());
        }

        Map<Long, String> nicknames = new HashMap<>();

        for (User user : userRepository.findAllById(userIds)) {
            nicknames.put(user.id(), user.nickname());
        }

        return nicknames;
    }
}
