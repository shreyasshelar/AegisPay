package com.aegispay.common.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AegisPayException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public AegisPayException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public AegisPayException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
