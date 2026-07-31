package com.yourname.aicommerce.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for {@code POST /api/auth/refresh} and {@code POST /api/auth/logout}.
 */
@Getter
@Setter
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
