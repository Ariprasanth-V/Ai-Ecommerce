package com.yourname.aicommerce.common.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Generic API response wrapper for all successful responses.
 *
 * <pre>
 * {
 *   "timestamp": "2026-07-29T05:30:00",
 *   "status": 200,
 *   "message": "Product retrieved successfully",
 *   "data": { ... }
 * }
 * </pre>
 *
 * @param <T> the type of the response payload
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    @Builder.Default
    private final int status = 200;

    private final String message;

    private final T data;

    // ── Convenience factory methods ──────────────────────────────────────

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message("Success")
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status(200)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> created(T data) {
        return ApiResponse.<T>builder()
                .status(201)
                .message("Created")
                .data(data)
                .build();
    }

    public static ApiResponse<Void> noContent() {
        return ApiResponse.<Void>builder()
                .status(204)
                .message("No Content")
                .build();
    }
}
