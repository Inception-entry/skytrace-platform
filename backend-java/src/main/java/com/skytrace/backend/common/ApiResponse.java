package com.skytrace.backend.common;

import java.time.LocalDateTime;

public record ApiResponse<T>(boolean success, String message, T data, LocalDateTime timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "success", data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null, LocalDateTime.now());
    }
}
