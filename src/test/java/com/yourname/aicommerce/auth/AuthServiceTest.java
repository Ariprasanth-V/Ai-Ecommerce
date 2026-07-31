package com.yourname.aicommerce.auth;

import com.yourname.aicommerce.auth.dto.AuthResponse;
import com.yourname.aicommerce.auth.dto.LoginRequest;
import com.yourname.aicommerce.auth.dto.RefreshRequest;
import com.yourname.aicommerce.auth.dto.RegisterRequest;
import com.yourname.aicommerce.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 * All collaborators are mocked; no Spring context is loaded.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RpbmctMzI=";

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuthenticationManager authenticationManager;

    private AuthService authService;
    private JwtService jwtService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(TEST_SECRET);
        props.setAccessTokenExpiryMs(900_000L);
        props.setRefreshTokenExpiryMs(604_800_000L);

        jwtService = new JwtService(props);
        passwordEncoder = new BCryptPasswordEncoder();

        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                props,
                authenticationManager);
    }

    // ── register ─────────────────────────────────────────────────────────

    @Test
    void register_success_saves_user_and_returns_tokens() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(buildRegisterRequest("new@example.com"));

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicate_email_throws_DuplicateResourceException() {
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(buildRegisterRequest("existing@example.com")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("existing@example.com");

        verify(userRepository, never()).save(any());
    }

    // ── login ─────────────────────────────────────────────────────────────

    @Test
    void login_success_returns_token_pair() {
        User user = buildUser("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        // authenticationManager.authenticate() does nothing (no exception = success)

        AuthResponse response = authService.login(buildLoginRequest("user@example.com", "Password1"));

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
    }

    @Test
    void login_wrong_password_throws_BadCredentialsException() {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authService.login(buildLoginRequest("user@example.com", "wrongpass")))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(anyString());
    }

    // ── refresh ───────────────────────────────────────────────────────────

    @Test
    void refresh_success_rotates_token() {
        String rawToken = "some-raw-refresh-token";
        String hash = jwtService.hashToken(rawToken);
        User user = buildUser("user@example.com");

        RefreshToken stored = RefreshToken.builder()
                .tokenHash(hash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(rawToken);

        AuthResponse response = authService.refresh(request);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(stored.getRevoked()).isTrue();  // old token revoked
    }

    @Test
    void refresh_revoked_token_throws_InvalidRefreshTokenException() {
        String rawToken = "revoked-token";
        String hash = jwtService.hashToken(rawToken);
        User user = buildUser("user@example.com");

        RefreshToken stored = RefreshToken.builder()
                .tokenHash(hash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(true)   // already used
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(rawToken);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("already been used");

        // Verify all sessions were revoked (replay detection response)
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void refresh_expired_token_throws_InvalidRefreshTokenException() {
        String rawToken = "expired-token";
        String hash = jwtService.hashToken(rawToken);
        User user = buildUser("user@example.com");

        RefreshToken stored = RefreshToken.builder()
                .tokenHash(hash)
                .user(user)
                .expiresAt(LocalDateTime.now().minusDays(1))  // expired
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(rawToken);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refresh_unknown_token_throws_InvalidRefreshTokenException() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("unknown-token");

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("not found");
    }

    // ── logout ────────────────────────────────────────────────────────────

    @Test
    void logout_revokes_existing_token() {
        String rawToken = "active-token";
        String hash = jwtService.hashToken(rawToken);
        User user = buildUser("user@example.com");

        RefreshToken stored = RefreshToken.builder()
                .tokenHash(hash).user(user)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(stored));

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(rawToken);
        authService.logout(request);

        assertThat(stored.getRevoked()).isTrue();
    }

    @Test
    void logout_unknown_token_does_not_throw() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("nonexistent");

        // Should silently succeed
        authService.logout(request);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Test");
        req.setLastName("User");
        req.setEmail(email);
        req.setPassword("Password1");
        return req;
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private User buildUser(String email) {
        return User.builder()
                .email(email)
                .password(passwordEncoder.encode("Password1"))
                .role(Role.CUSTOMER)
                .active(true)
                .build();
    }
}
