package com.aegispay.user.exception;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.dto.ErrorResponse;
import com.aegispay.common.domain.exception.AegisPayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AegisPayException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(AegisPayException ex) {
        log.warn("Domain error: code={} message={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(ApiResponse.error(
                ErrorResponse.builder()
                        .errorCode(ex.getErrorCode())
                        .message(ex.getMessage())
                        .httpStatus(ex.getHttpStatus().value())
                        .build()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("VALIDATION_FAILED")
                .message("Request validation failed.")
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(ApiResponse.error(error));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("MISSING_HEADER")
                .message("Required header is missing: " + ex.getHeaderName())
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .build();
        return ResponseEntity.badRequest().body(ApiResponse.error(error));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                ErrorResponse.builder()
                        .errorCode("UNAUTHORIZED")
                        .message("Authentication required.")
                        .httpStatus(HttpStatus.UNAUTHORIZED.value())
                        .build()
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                ErrorResponse.builder()
                        .errorCode("FORBIDDEN")
                        .message("You do not have permission to perform this action.")
                        .httpStatus(HttpStatus.FORBIDDEN.value())
                        .build()
        ));
    }

    /**
     * Catches DB unique-constraint and foreign-key violations before they surface as 500.
     *
     * <p>The most common trigger is a concurrent first-time registration: two requests for
     * the same JWT {@code sub} arrive simultaneously and both pass the
     * {@code findByExternalId} check before either writes.  The second write violates the
     * UNIQUE constraint on {@code external_id} or {@code email}.
     *
     * <p>The controller's own try-catch already handles the concurrent-registration race on
     * {@code /register} and retries the lookup there.  This handler is a safety net for any
     * other endpoint that performs a guarded write without a controller-level catch.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation (likely concurrent write or duplicate key): {}",
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                ErrorResponse.builder()
                        .errorCode("DATA_CONFLICT")
                        .message("A resource with the same identifier already exists.")
                        .httpStatus(HttpStatus.CONFLICT.value())
                        .build()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(
                ErrorResponse.builder()
                        .errorCode("INTERNAL_SERVER_ERROR")
                        .message("An unexpected error occurred. Please try again later.")
                        .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .build()
        ));
    }
}
