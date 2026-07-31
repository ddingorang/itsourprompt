package com.promptstudio.global.exception;

import com.promptstudio.attempt.exception.AttemptAlreadySubmittedException;
import com.promptstudio.attempt.exception.AttemptHasNoTurnsException;
import com.promptstudio.attempt.exception.AttemptNotFoundException;
import com.promptstudio.attempt.exception.CodeRunInProgressException;
import com.promptstudio.attempt.exception.CodeRunNotFoundException;
import com.promptstudio.attempt.exception.DuplicateRequestException;
import com.promptstudio.attempt.exception.FeedbackGenerationInProgressException;
import com.promptstudio.attempt.exception.FeedbackNotFoundException;
import com.promptstudio.problem.exception.InactiveProblemException;
import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.attempt.port.CodeGenerationException;
import com.promptstudio.attempt.port.CodeGenerationTimeoutException;
import com.promptstudio.attempt.port.FeedbackGenerationException;
import com.promptstudio.attempt.port.FeedbackTimeoutException;
import com.promptstudio.user.exception.DuplicateEmailException;
import com.promptstudio.user.exception.DuplicateUsernameException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(MethodArgumentNotValidException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "invalid-request",
                "요청 값이 올바르지 않습니다."
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler({CodeGenerationException.class, FeedbackGenerationException.class})
    public ResponseEntity<ApiErrorResponse> handleGenerationFailure(RuntimeException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "ai-provider-error",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    @ExceptionHandler(CodeGenerationTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleCodeGenerationTimeout(CodeGenerationTimeoutException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "run-timeout",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    @ExceptionHandler(FeedbackTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleFeedbackTimeout(FeedbackTimeoutException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "feedback-timeout",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected API exception", exception);

        ApiErrorResponse response = new ApiErrorResponse(
                "internal-server-error",
                "서버 내부 오류가 발생했습니다. X-Request-Id를 포함해 관리자에게 문의해주세요."
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
