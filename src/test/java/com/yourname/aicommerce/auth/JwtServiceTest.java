package com.yourname.aicommerce.auth;

import com.yourname.aicommerce.auth.dto.AuthResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}.
 * Uses a known test secret and a real User instance — no mocking needed.
 */
class JwtServiceTest {

    // 32-byte base64 test secret (safe for HS256 key length requirement)
    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RpbmctMzI=";

    private JwtService jwtService;
    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        props.setAccessTokenExpiryMs(900_000L);      // 15 min
        props.setRefreshTokenExpiryMs(604_800_000L); // 7 days

        jwtService = new JwtService(props);

        testUser = User.builder()
                .email("user@example.com")
                .password("hashed")
                .role(Role.CUSTOMER)
                .active(true)
                .build();
    }

    @Test
    void generateAccessToken_produces_non_null_token() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(token).isNotBlank();
    }

    @Test
    void extractEmail_returns_correct_subject() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void isTokenValid_returns_true_for_valid_token() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.isTokenValid(token, testUser)).isTrue();
    }

    @Test
    void isTokenValid_returns_false_for_wrong_user() {
        String token = jwtService.generateAccessToken(testUser);

        UserDetails otherUser = User.builder()
                .email("other@example.com")
                .password("hashed")
                .role(Role.CUSTOMER)
                .active(true)
                .build();

        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void isTokenValid_returns_false_for_expired_token() {
        // Create a JwtService with 0ms expiry (immediately expired)
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret(TEST_SECRET);
        shortProps.setAccessTokenExpiryMs(0L);
        shortProps.setRefreshTokenExpiryMs(0L);
        JwtService shortService = new JwtService(shortProps);

        String token = shortService.generateAccessToken(testUser);
        assertThat(shortService.isTokenValid(token, testUser)).isFalse();
    }

    @Test
    void isTokenValid_returns_false_for_tampered_token() {
        String token = jwtService.generateAccessToken(testUser);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThat(jwtService.isTokenValid(tampered, testUser)).isFalse();
    }

    @Test
    void generateRefreshTokenRaw_produces_unique_values() {
        String t1 = jwtService.generateRefreshTokenRaw();
        String t2 = jwtService.generateRefreshTokenRaw();
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void hashToken_produces_64_char_hex_string() {
        String hash = jwtService.hashToken("some-raw-token");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void hashToken_is_deterministic() {
        String raw = "deterministic-input";
        assertThat(jwtService.hashToken(raw)).isEqualTo(jwtService.hashToken(raw));
    }

    @Test
    void hashToken_different_inputs_produce_different_hashes() {
        assertThat(jwtService.hashToken("aaa")).isNotEqualTo(jwtService.hashToken("bbb"));
    }
}
