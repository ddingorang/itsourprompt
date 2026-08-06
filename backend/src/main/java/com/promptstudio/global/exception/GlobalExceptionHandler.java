package com.promptstudio.global.exception;

import com.promptstudio.attempt.exception.AttemptAlreadySubmittedException;
import com.promptstudio.attempt.exception.AttemptHasNoTurnsException;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.exception.CodeGenerationInProgressException;
import com.promptstudio.attempt.exception.CodeRunInProgressException;
import com.promptstudio.attempt.exception.CodeRunNotFoundException;
import com.promptstudio.attempt.exception.DuplicateRequestException;
import com.promptstudio.attempt.exception.FeedbackGenerationInProgressException;
import com.promptstudio.attempt.exception.FeedbackNotFoundException;
import com.promptstudio.attempt.exception.TurnNotFoundException;
import com.promptstudio.attempt.exception.PromptScopeRejectedException;
import com.promptstudio.ai.PromptScopeValidationException;
import com.promptstudio.problem.exception.InactiveProblemException;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.ranking.exception.RankingLimitOutOfRangeException;
import com.promptstudio.relay.exception.NotRelayHostException;
import com.promptstudio.relay.exception.NotRelayParticipantException;
import com.promptstudio.relay.exception.RelayParticipantLeftException;
import com.promptstudio.relay.exception.RelayGameNotPlayingException;
import com.promptstudio.relay.exception.RelayGameNotStartedException;
import com.promptstudio.relay.exception.RelayNotEnoughParticipantsException;
import com.promptstudio.relay.exception.RelayNotYourTurnException;
import com.promptstudio.relay.exception.RelayRoomAlreadyStartedException;
import com.promptstudio.relay.exception.RelayRoomFullException;
import com.promptstudio.relay.exception.RelayRoomNotFoundException;
import com.promptstudio.attempt.port.CodeGenerationException;
import com.promptstudio.attempt.port.CodeGenerationTimeoutException;
import com.promptstudio.attempt.port.FeedbackGenerationException;
import com.promptstudio.attempt.port.FeedbackTimeoutException;
import com.promptstudio.user.exception.DuplicateEmailException;
import com.promptstudio.user.exception.DuplicateUsernameException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String AI_GENERATION_FAILED_MESSAGE = "AI 응답 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.";
    private static final String CODE_GENERATION_TIMEOUT_MESSAGE = "AI 코드 생성 요청 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.";
    private static final String FEEDBACK_TIMEOUT_MESSAGE = "AI 피드백 생성 요청 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.";
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProblemNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProblemNotFound(ProblemNotFoundException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "problem-not-found",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(InactiveProblemException.class)
    public ResponseEntity<ApiErrorResponse> handleInactiveProblem(InactiveProblemException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "problem-inactive",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(AttemptNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAttemptNotFound(AttemptNotFoundException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "attempt-not-found",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(TurnNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTurnNotFound(TurnNotFoundException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "turn-not-found",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(FeedbackNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleFeedbackNotFound(FeedbackNotFoundException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "feedback-not-found",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(AttemptHasNoTurnsException.class)
    public ResponseEntity<ApiErrorResponse> handleAttemptHasNoTurns(AttemptHasNoTurnsException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "attempt-has-no-turns",
                exception.getMessage()
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AttemptAlreadySubmittedException.class)
    public ResponseEntity<ApiErrorResponse> handleAttemptAlreadySubmitted(AttemptAlreadySubmittedException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "attempt-already-submitted",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(FeedbackGenerationInProgressException.class)
    public ResponseEntity<ApiErrorResponse> handleFeedbackGenerationInProgress(
            FeedbackGenerationInProgressException exception
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                "feedback-in-progress",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(CodeGenerationInProgressException.class)
    public ResponseEntity<ApiErrorResponse> handleCodeGenerationInProgress(
            CodeGenerationInProgressException exception
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                "code-generation-in-progress",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(PromptScopeRejectedException.class)
    public ResponseEntity<ApiErrorResponse> handlePromptScopeRejected(PromptScopeRejectedException exception) {
        ApiErrorResponse response = new ApiErrorResponse("prompt-out-of-scope", exception.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(PromptScopeValidationException.class)
    public ResponseEntity<ApiErrorResponse> handlePromptScopeValidationFailure(PromptScopeValidationException exception) {
        ApiErrorResponse response = new ApiErrorResponse("prompt-scope-validation-failed", exception.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(CodeRunNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCodeRunNotFound(CodeRunNotFoundException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "code-run-not-found",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(CodeRunInProgressException.class)
    public ResponseEntity<ApiErrorResponse> handleCodeRunInProgress(CodeRunInProgressException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "code-run-in-progress",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateRequest(DuplicateRequestException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "duplicate-request",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(RelayRoomNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRelayRoomNotFound(RelayRoomNotFoundException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "relay-room-not-found",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(RelayRoomFullException.class)
    public ResponseEntity<ApiErrorResponse> handleRelayRoomFull(RelayRoomFullException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "relay-room-full",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(RelayRoomAlreadyStartedException.class)
    public ResponseEntity<ApiErrorResponse> handleRelayRoomAlreadyStarted(RelayRoomAlreadyStartedException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "relay-room-already-started",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * 게임 중 이탈한 참가자의 재입장. 이탈은 확답을 받은 최종 결정이라 되돌아올 수 없다.
     */
    @ExceptionHandler(RelayParticipantLeftException.class)
    public ResponseEntity<ApiErrorResponse> handleRelayParticipantLeft(RelayParticipantLeftException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "relay-participant-left",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 방의 존재는 입장 전에도 조회할 수 있으므로 404로 숨기지 않고 403으로 거절한다.
     */
    @ExceptionHandler(NotRelayParticipantException.class)
    public ResponseEntity<ApiErrorResponse> handleNotRelayParticipant(NotRelayParticipantException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "relay-not-participant",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(NotRelayHostException.class)
    public ResponseEntity<ApiErrorResponse> handleNotRelayHost(NotRelayHostException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "relay-not-host",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(RelayNotEnoughParticipantsException.class)
    public ResponseEntity<ApiErrorResponse> handleRelayNotEnoughParticipants(
            RelayNotEnoughParticipantsException exception
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                "relay-not-enough-participants",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(RelayNotYourTurnException.class)
    public ResponseEntity<ApiErrorResponse> handleRelayNotYourTurn(RelayNotYourTurnException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "relay-not-your-turn",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(RelayGameNotPlayingException.class)
    public ResponseEntity<ApiErrorResponse> handleRelayGameNotPlaying(RelayGameNotPlayingException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "relay-not-playing",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(RelayGameNotStartedException.class)
    public ResponseEntity<ApiErrorResponse> handleRelayGameNotStarted(RelayGameNotStartedException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "relay-not-started",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateUsername(DuplicateUsernameException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "duplicate-username",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEmail(DuplicateEmailException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "duplicate-email",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * 로그인 실패(아이디 없음/비밀번호 불일치). DaoAuthenticationProvider가
     * UsernameNotFoundException을 BadCredentialsException으로 감싸므로 이 하나로 두 경우를 처리한다.
     * 계정 존재 여부를 노출하지 않기 위해 어느 쪽이든 동일한 메시지를 반환한다.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "bad-credentials",
                "아이디 또는 비밀번호가 올바르지 않습니다."
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * 동시 가입 레이스 안전망: 서비스 계층 중복 검사를 동시에 통과한 두 요청 중
     * 늦은 쪽이 users의 UNIQUE 제약에 걸리면 여기서 409로 변환한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "duplicate-user",
                "이미 사용 중인 아이디 또는 이메일입니다."
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * 쿼리 파라미터 범위 검사는 컨트롤러가 직접 한다. {@code @Validated}+{@code @Min}을 쓰면
     * ConstraintViolationException이 나는데 그 핸들러가 없어 catch-all을 타고 500이 된다.
     */
    @ExceptionHandler(RankingLimitOutOfRangeException.class)
    public ResponseEntity<ApiErrorResponse> handleRankingLimitOutOfRange(RankingLimitOutOfRangeException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "invalid-request",
                exception.getMessage()
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(MethodArgumentNotValidException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "invalid-request",
                "요청 값이 올바르지 않습니다."
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 읽지 못한 요청 값. 본문이 깨진 JSON이거나(HttpMessageNotReadable), 경로 변수·쿼리 파라미터가
     * 선언한 타입으로 변환되지 않는 경우(MethodArgumentTypeMismatch)다.
     *
     * <p>둘 다 다른 표준 웹 예외와 달리 ErrorResponse를 구현하지 않아 catch-all의 가드에 걸리지 않는다.
     * 전용 핸들러가 없으면 클라이언트가 잘못 부른 요청이 500 + ERROR 로그로 쌓여, 정작 진짜 에러를
     * 찾을 수 없게 된다 — 타입 있는 경로 변수를 쓰는 엔드포인트가 이미 열두 곳이다.
     *
     * <p>예외 메시지는 문제가 된 입력 조각을 그대로 인용하므로 응답에도 로그에도 싣지 않는다.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequestValue(Exception exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "invalid-request",
                "요청 값이 올바르지 않습니다."
        );

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * AI 생성 실패는 같은 요청을 다시 보내면 통과하는 경우가 많다. 실패 유형은 내부 로그에만 남기고,
     * 응답에는 재시도 안내만 싣는다.
     */
    @ExceptionHandler({CodeGenerationException.class, FeedbackGenerationException.class})
    public ResponseEntity<ApiErrorResponse> handleGenerationFailure(RuntimeException exception) {
        log.warn("AI 생성 실패를 502로 변환합니다: {}", exception.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                "ai-provider-error",
                AI_GENERATION_FAILED_MESSAGE
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    /**
     * 타임아웃 문구는 예외 메시지가 아니라 고정 상수를 쓴다 — FeedbackTimeoutException의 메시지가
     * 영문이라 그대로 내보내면 사용자에게 영문이 나간다. code는 프런트의 재시도 분기가 걸려 있어 그대로 둔다.
     */
    @ExceptionHandler(CodeGenerationTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleCodeGenerationTimeout(CodeGenerationTimeoutException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "run-timeout",
                CODE_GENERATION_TIMEOUT_MESSAGE
        );

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    @ExceptionHandler(FeedbackTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleFeedbackTimeout(FeedbackTimeoutException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "feedback-timeout",
                FEEDBACK_TIMEOUT_MESSAGE
        );

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        // Spring이 이미 상태 코드를 아는 표준 웹 예외(404·405·415 등)는 그 상태 코드를 그대로 쓰고
        // 스택트레이스를 남기지 않는다 — 클라이언트 잘못이지 버그가 아니다. 가드가 없으면 이 요청들이
        // 전부 500 + ERROR로 쌓여 정작 진짜 에러를 찾을 수 없게 된다.
        //
        // 5xx를 내는 ErrorResponse 구현체(AsyncRequestTimeoutException 503 등)는 여기서 걸러
        // 아래 ERROR 경로로 보낸다. 예외를 다시 던지지 않는 이유는 /error 재디스패치로 응답 본문이
        // ApiErrorResponse에서 스프링 기본 오류 본문으로 바뀌기 때문이다.
        if (exception instanceof ErrorResponse errorResponse
                && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = errorResponse.getStatusCode();

            // 예외 메시지에는 사용자가 보낸 입력 조각이 인용될 수 있어 클래스 이름만 남긴다.
            log.atWarn()
                    .addKeyValue("status", status.value())
                    .addKeyValue("exceptionType", exception.getClass().getSimpleName())
                    .log("표준 웹 예외를 상태 코드로 변환합니다");

            return ResponseEntity.status(status).body(new ApiErrorResponse(codeFor(status), messageFor(status)));
        }

        log.error("Unexpected API exception", exception);

        ApiErrorResponse response = new ApiErrorResponse(
                "internal-server-error",
                "서버 내부 오류가 발생했습니다. X-Request-Id를 포함해 관리자에게 문의해주세요."
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "not-found";
            case 405 -> "method-not-allowed";
            case 406 -> "not-acceptable";
            case 415 -> "unsupported-media-type";
            default -> "invalid-request";
        };
    }

    /**
     * 예외 메시지 대신 상태별 고정 문구를 쓴다. 프런트는 모르는 code를 unknown-error로 받고
     * message를 그대로 보여주므로 이 표가 늘어도 프런트 변경이 없다.
     */
    private String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "요청하신 경로를 찾을 수 없습니다.";
            case 405 -> "지원하지 않는 요청 메서드입니다.";
            case 406 -> "지원하지 않는 응답 형식입니다.";
            case 415 -> "지원하지 않는 요청 형식입니다.";
            default -> "요청 값이 올바르지 않습니다.";
        };
    }
}
