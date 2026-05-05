package com.aegispay.common.domain.exception;

import org.springframework.http.HttpStatus;

public class DuplicateIdempotencyKeyException extends AegisPayException {

    public DuplicateIdempotencyKeyException(String idempotencyKey) {
        super("DUPLICATE_IDEMPOTENCY_KEY",
              "Request with idempotency key already processed: " + idempotencyKey,
              HttpStatus.CONFLICT);
    }
}
