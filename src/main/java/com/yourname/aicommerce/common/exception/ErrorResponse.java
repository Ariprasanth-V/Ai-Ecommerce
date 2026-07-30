package com.yourname.aicommerce.common.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Consistent JSON error response shape returned by all exception handlers.
 *
 * <pre>
 * {
 *   "timestamp": "2026-07-29T05:30:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Product with id 42 not found",
 *   "path": "/api/v1/products/42"
 * }
 * </pre>
 */
@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;

    private final int status;

    private final String error;

    private final String message;

    private final String path;
}
