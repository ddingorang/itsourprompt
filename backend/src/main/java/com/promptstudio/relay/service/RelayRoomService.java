package com.promptstudio.relay.service;

import com.promptstudio.problem.domain.Problem;
import com.promptstudio.problem.exception.InactiveProblemException;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.repository.ProblemRepository;
import com.promptstudio.relay.domain.RelayParticipant;
import com.promptstudio.relay.domain.RelayRoom;
import com.promptstudio.relay.domain.RelayRoomSummary;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.exception.NotRelayParticipantException;
import com.promptstudio.relay.exception.RelayRoomAlreadyStartedException;
import com.promptstudio.relay.exception.RelayRoomFullException;
import com.promptstudio.relay.exception.RelayRoomNotFoundException;
import com.promptstudio.relay.repository.RelayParticipantRepository;
import com.promptstudio.relay.repository.RelayRoomRepository;
import com.promptstudio.user.domain.User;
import com.promptstudio.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 방 개설·입장·퇴장과 방 상태 조회. 게임 진행(턴·채점·피드백)은 이 서비스가 다루지 않는다.
 */
@Service
public class RelayRoomService {

    private static final Logger log = LoggerFactory.getLogger(RelayRoomService.class);

    private final RelayRoomRepository roomRepository;
    private final RelayParticipantRepository participantRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final RelayRoomViewFactory viewFactory;
    private final RelayGameWriter gameWriter;
    private final RelayFeedbackService feedbackService;
    private final ApplicationEventPublisher eventPublisher;

    public RelayRoomService(
            RelayRoomRepository roomRepository,
            RelayParticipantRepository participantRepository,
            ProblemRepository problemRepository,
            UserRepository userRepository,
            RelayRoomViewFactory viewFactory,
            RelayGameWriter gameWriter,
            RelayFeedbackService feedbackService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.roomRepository = roomRepository;
        this.participantRepository = participantRepository;
        this.problemRepository = problemRepository;
        this.userRepository = userRepository;
        this.viewFactory = viewFactory;
        this.gameWriter = gameWriter;
        this.feedbackService = feedbackService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RelayRoomView openRoom(Long problemId, Long hostUserId, int totalLaps, int maxParticipants) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ProblemNotFoundException(problemId));

        if (!problem.active()) {
            throw new InactiveProblemException(problemId);
        }

        RelayRoom room = roomRepository.save(
                RelayRoom.open(problemId, hostUserId, totalLaps, maxParticipants));

        // 방장은 개설과 동시에 첫 참가자가 된다. 방을 만드는 것과 참가하는 것을 따로 두면
        // 방장이 입장을 빠뜨린 방이 생기고, 그 방은 1번 좌석의 주인이 없다.
        participantRepository.save(RelayParticipant.join(room.id(), hostUserId));

        log.info("[RELAY] room opened | roomId={} | problemId={} | hostUserId={} | laps={} | maxParticipants={}",
                room.id(), problemId, hostUserId, totalLaps, maxParticipants);

        return toView(room);
    }

    @Transactional(readOnly = true)
    public RelayRoomView getRoom(Long roomId) {
        return toView(getRoomEntity(roomId));
    }

    /**
     * 로비에 보여줄 입장 가능한 방 목록. WAITING이면서 사람이 있는 방만 —
     * 전원이 나가 버려진 대기방은 WAITING인 채 영원히 남는데, 그건 방이 아니라 잔해다.
     */
    @Transactional(readOnly = true)
    public List<RelayRoomSummary> listJoinableRooms() {
        List<RelayRoom> rooms = roomRepository.findWaitingRooms();

        if (rooms.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = new ArrayList<>();
        List<Long> problemIds = new ArrayList<>();
        List<Long> hostIds = new ArrayList<>();

        for (RelayRoom room : rooms) {
            roomIds.add(room.id());
            problemIds.add(room.problemId());
            hostIds.add(room.hostUserId());
        }

        Map<Long, Integer> headcounts = new HashMap<>();

        for (RelayParticipant participant : participantRepository.findByRoomIdInAndLeftAtIsNull(roomIds)) {
            headcounts.merge(participant.roomId(), 1, Integer::sum);
        }

        Map<Long, String> problemTitles = new HashMap<>();

        for (Problem problem : problemRepository.findAllById(problemIds)) {
            problemTitles.put(problem.id(), problem.title());
        }

        Map<Long, String> hostNicknames = new HashMap<>();

        for (User user : userRepository.findAllById(hostIds)) {
            hostNicknames.put(user.id(), user.nickname());
        }

        List<RelayRoomSummary> summaries = new ArrayList<>();

        for (RelayRoom room : rooms) {
            int headcount = headcounts.getOrDefault(room.id(), 0);

            if (headcount == 0) {
                continue;
            }

            summaries.add(new RelayRoomSummary(
                    room.id(),
                    room.problemId(),
                    problemTitles.get(room.problemId()),
                    room.hostUserId(),
                    hostNicknames.get(room.hostUserId()),
                    headcount,
                    room.maxParticipants(),
                    room.totalLaps(),
                    room.createdAt()
            ));
        }

        return summaries;
    }

    /**
     * 입장은 멱등하다. 이미 참가자면 새로 넣지 않고 현재 상태를 그대로 돌려준다 — 새로고침이나
     * 재접속으로 같은 요청이 다시 오는 것이 정상 경로이고, 그걸 409로 막으면 방에 돌아올 수 없다.
     *
     * <p>정원 검사 때문에 방 행에 쓰기 락을 걸고 시작한다({@code findByIdForUpdate} 주석 참고).
     */
    @Transactional
    public RelayRoomView join(Long roomId, Long userId) {
        RelayRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RelayRoomNotFoundException(roomId));

        Optional<RelayParticipant> existing = participantRepository.findByRoomIdAndUserId(roomId, userId);

        if (existing.isPresent()) {
            RelayParticipant participant = existing.get();

            // 게임 중 이탈했다 돌아온 참가자. 낙인을 지우지 않으면 이탈 좌석 즉시 스킵에
            // 걸려, 돌아왔는데도 자기 차례가 오는 족족 건너뛰어진다.
            if (participant.hasLeft()) {
                participant.rejoin();
                participantRepository.save(participant);

                log.info("[RELAY] participant rejoined | roomId={} | userId={}", roomId, userId);

                return publishChanged(toView(room));
            }

            return toView(room);
        }

        // 시작 후 새 입장은 받지 않는다. 좌석이 이미 확정되어 끼워 넣을 자리가 없다.
        if (!room.isWaiting()) {
            throw new RelayRoomAlreadyStartedException(roomId);
        }

        if (participantRepository.countActiveByRoomId(roomId) >= room.maxParticipants()) {
            throw new RelayRoomFullException(roomId, room.maxParticipants());
        }

        participantRepository.save(RelayParticipant.join(roomId, userId));

        log.info("[RELAY] participant joined | roomId={} | userId={}", roomId, userId);

        return publishChanged(toView(room));
    }

    /**
     * 시작 전에는 행을 지우고, 시작 후에는 좌석을 남긴 채 이탈 시각만 기록한다.
     * 좌석을 지우면 남은 주자들의 좌석 번호가 밀려 진행 인덱스와 어긋난다.
     */
    @Transactional
    public RelayRoomView leave(Long roomId, Long userId) {
        RelayRoom room = getRoomEntity(roomId);
        List<RelayParticipant> participants =
                new ArrayList<>(participantRepository.findByRoomIdOrderByJoinedAt(roomId));
        RelayParticipant participant = findParticipant(participants, userId)
                .orElseThrow(() -> new NotRelayParticipantException(roomId));

        // 시작 후에는 좌석을 남긴 채 이탈 시각만 기록한다. 좌석을 지우면 남은 주자들의 좌석 번호가
        // 밀려 진행 인덱스와 어긋난다.
        if (!room.isWaiting()) {
            participant.leave();
            participantRepository.save(participant);

            log.info("[RELAY] participant left mid-game | roomId={} | userId={} | status={}",
                    roomId, userId, room.status());

            RelayRoomView view = publishChanged(toView(room, participants));

            // 나간 사람이 지금 차례였다면 즉시 건너뛴다. 마감까지 기다리는 것은 있는 사람의
            // 몫이다. 생성·채점 중(TURN_GENERATING·TURN_GRADING)이면 안에서 무시되고,
            // 그 턴이 끝난 전진 경로가 이탈 좌석을 알아서 건너뛴다.
            RelayRoomView afterSkip = gameWriter.skipAbsentTurns(roomId);

            if (afterSkip != null) {
                // 연쇄 스킵이 마지막 턴을 넘겼으면 피드백 생성으로 이어져야 한다.
                feedbackService.maybeStartFeedback(afterSkip);

                return afterSkip;
            }

            return view;
        }

        participantRepository.delete(participant);
        participants.remove(participant);

        if (room.isHost(userId)) {
            delegateHost(room, participants);
        }

        log.info("[RELAY] participant left | roomId={} | userId={} | remaining={}",
                roomId, userId, participants.size());

        // 남은 참가자는 삭제 전에 읽어 둔 목록에서 만든다. 삭제가 플러시되기 전에 다시 조회하면
        // 나간 사람이 그대로 실려 방금 지운 상태를 브로드캐스트할 수 있다.
        return publishChanged(toView(room, participants));
    }

    /**
     * 가장 먼저 입장한 남은 참가자에게 넘긴다. 아무도 없으면 그대로 둔다 —
     * 빈 방은 시작할 사람도 없으므로 해가 없다.
     */
    private void delegateHost(RelayRoom room, List<RelayParticipant> remaining) {
        if (remaining.isEmpty()) {
            return;
        }

        Long successor = remaining.getFirst().userId();
        room.delegateHostTo(successor);
        roomRepository.save(room);

        log.info("[RELAY] host delegated | roomId={} | newHostUserId={}", room.id(), successor);
    }

    private Optional<RelayParticipant> findParticipant(List<RelayParticipant> participants, Long userId) {
        for (RelayParticipant participant : participants) {
            if (participant.userId().equals(userId)) {
                return Optional.of(participant);
            }
        }

        return Optional.empty();
    }

    /**
     * 방을 새로 연 직후에는 발행하지 않는다. 그 방의 소켓에 붙어 있을 수 있는 사람이 아직 없다.
     */
    private RelayRoomView publishChanged(RelayRoomView room) {
        eventPublisher.publishEvent(new RelayRoomChanged(room));

        return room;
    }

    private RelayRoom getRoomEntity(Long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new RelayRoomNotFoundException(roomId));
    }

    private RelayRoomView toView(RelayRoom room) {
        return viewFactory.toView(room);
    }

    private RelayRoomView toView(RelayRoom room, List<RelayParticipant> participants) {
        return viewFactory.toView(room, participants);
    }
}
