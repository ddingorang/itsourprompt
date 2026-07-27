package com.promptstudio.global.exception;

import com.promptstudio.problem.exception.ProblemNotFoundException;
import com.promptstudio.problem.port.CodeGenerationException;
import com.promptstudio.problem.port.CodeGenerationTimeoutException;
import com.promptstudio.problem.port.FeedbackGenerationException;
import com.promptstudio.problem.port.FeedbackTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProblemNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProblemNotFound(ProblemNotFoundException exception) {
        ApiErrorResponse response = new ApiErrorResponse(
                "problem-not-found",
                exception.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
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
}
