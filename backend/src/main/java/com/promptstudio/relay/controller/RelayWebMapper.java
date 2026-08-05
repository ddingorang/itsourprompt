package com.promptstudio.relay.controller;

import com.promptstudio.attempt.domain.AttemptView;
import com.promptstudio.problem.domain.ProblemFile;
import com.promptstudio.relay.controller.response.RelayCodeResponse;
import com.promptstudio.relay.controller.response.RelayFeedbackResponse;
import com.promptstudio.relay.controller.response.RelayGradingResponse;
import com.promptstudio.relay.controller.response.RelayRoomListResponse;
import com.promptstudio.relay.controller.response.RelayRoomResponse;
import com.promptstudio.relay.controller.response.RelayTurnListResponse;
import com.promptstudio.relay.controller.response.RelayTurnResponse;
import com.promptstudio.relay.domain.RelayRoomSummary;
import com.promptstudio.relay.domain.RelayRoomView;
import com.promptstudio.relay.service.RelayFeedbackView;
import com.promptstudio.relay.service.RelayGradingFinished;
import com.promptstudio.relay.service.RelayTurnRecord;
import com.promptstudio.relay.service.RelayTurnResult;
import com.promptstudio.relay.service.RelayTurnSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RelayWebMapper {

    public RelayRoomResponse toRoomResponse(RelayRoomView room) {
        List<RelayRoomResponse.RelayParticipantResponse> participants = new ArrayList<>();

        for (RelayRoomView.RelayParticipantView participant : room.participants()) {
            participants.add(new RelayRoomResponse.RelayParticipantResponse(
                    participant.userId(),
                    participant.nickname(),
                    participant.seatOrder(),
                    participant.joinedAt(),
                    participant.left()
            ));
        }

        return new RelayRoomResponse(
                room.id(),
                room.name(),
                room.problemId(),
                room.hostUserId(),
                room.status(),
                room.totalLaps(),
                room.maxParticipants(),
                room.seatCount(),
                room.currentTurnIndex(),
                room.currentSeat(),
                room.currentLap(),
                room.totalTurns(),
                room.baselinePassed(),
                room.baselineTotal(),
                room.turnDeadline(),
                participants,
                room.createdAt(),
                room.startedAt(),
                room.finishedAt()
        );
    }

    public RelayRoomListResponse toRoomListResponse(List<RelayRoomSummary> summaries) {
        List<RelayRoomListResponse.RelayRoomSummaryResponse> rooms = new ArrayList<>();

        for (RelayRoomSummary summary : summaries) {
            rooms.add(new RelayRoomListResponse.RelayRoomSummaryResponse(
                    summary.roomId(),
                    summary.name(),
                    summary.problemId(),
                    summary.problemTitle(),
                    summary.hostUserId(),
                    summary.hostNickname(),
                    summary.participantCount(),
                    summary.maxParticipants(),
                    summary.totalLaps(),
                    summary.createdAt()
            ));
        }

        return new RelayRoomListResponse(rooms);
    }

    public RelayTurnResponse toTurnResponse(RelayTurnResult result) {
        return new RelayTurnResponse(toTurnSummaryResponse(result.summary()), toRoomResponse(result.room()));
    }

    public RelayTurnResponse.RelayTurnSummaryResponse toTurnSummaryResponse(RelayTurnSummary summary) {
        return new RelayTurnResponse.RelayTurnSummaryResponse(
                summary.turnIndex(),
                summary.seatOrder(),
                summary.lap(),
                summary.authorUserId(),
                summary.aiSummary(),
                summary.changedPaths()
        );
    }

    public RelayCodeResponse toCodeResponse(AttemptView attempt) {
        List<RelayCodeResponse.RelayFileResponse> files = new ArrayList<>();

        for (ProblemFile file : attempt.files()) {
            files.add(new RelayCodeResponse.RelayFileResponse(file.path(), file.content()));
        }

        return new RelayCodeResponse(attempt.turns().size(), files);
    }

    public RelayGradingResponse toGradingOutcomeResponse(RelayGradingFinished.Outcome outcome) {
        List<RelayGradingResponse.FailedCaseResponse> failedCases = new ArrayList<>();

        for (RelayGradingFinished.FailedCase failedCase : outcome.failedCases()) {
            failedCases.add(new RelayGradingResponse.FailedCaseResponse(failedCase.name(), failedCase.message()));
        }

        return new RelayGradingResponse(
                outcome.turnIndex(),
                outcome.authorUserId(),
                outcome.runStatus(),
                outcome.passed(),
                outcome.total(),
                outcome.delta(),
                failedCases,
                outcome.skipped()
        );
    }

    public RelayTurnListResponse toTurnListResponse(List<RelayTurnRecord> records) {
        List<RelayTurnListResponse.RelayTurnRecordResponse> turns = new ArrayList<>();

        for (RelayTurnRecord record : records) {
            turns.add(new RelayTurnListResponse.RelayTurnRecordResponse(
                    record.turnIndex(),
                    record.seatOrder(),
                    record.lap(),
                    record.authorUserId(),
                    record.passedCount(),
                    record.totalCount(),
                    record.delta(),
                    record.skipped(),
                    record.startedAt(),
                    record.finishedAt()
            ));
        }

        return new RelayTurnListResponse(turns);
    }

    public RelayFeedbackResponse toFeedbackResponse(RelayFeedbackView feedback) {
        List<RelayFeedbackResponse.RelayTurnFeedbackResponse> turns = new ArrayList<>();

        for (RelayFeedbackView.TurnFeedback turn : feedback.turns()) {
            turns.add(new RelayFeedbackResponse.RelayTurnFeedbackResponse(
                    turn.turnIndex(),
                    turn.seatOrder(),
                    turn.lap(),
                    turn.authorUserId(),
                    turn.nickname(),
                    turn.feedback(),
                    turn.passedCount(),
                    turn.totalCount(),
                    turn.delta()
            ));
        }

        return new RelayFeedbackResponse(feedback.overall(), turns);
    }
}
