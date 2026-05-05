package com.aegispay.common.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorResponse error;
    private final String correlationId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Instant timestamp;

    private ApiResponse(boolean success, T data, ErrorResponse error, String correlationId) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.correlationId = correlationId;
        this.timestamp = Instant.now();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, String correlationId) {
        return new ApiResponse<>(true, data, null, correlationId);
    }

    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error, null);
    }

    public static <T> ApiResponse<T> error(ErrorResponse error, String correlationId) {
        return new ApiResponse<>(false, null, error, correlationId);
    }
}
