package com.yourname.aicommerce.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * Response body returned by register, login, and refresh endpoints.
 */
@Getter
@Builder
public class AuthResponse {

    @JsonProperty("access_token")
    private final String accessToken;

    @JsonProperty("refresh_token")
    private final String refreshToken;

    @JsonProperty("token_type")
    @Builder.Default
    private final String tokenType = "Bearer";

    /** Access token lifetime in seconds (for client-side expiry tracking). */
    @JsonProperty("expires_in")
    private final long expiresIn;
}
