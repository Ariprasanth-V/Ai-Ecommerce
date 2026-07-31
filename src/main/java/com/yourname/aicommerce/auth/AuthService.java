package com.yourname.aicommerce.auth;

import com.yourname.aicommerce.auth.dto.AuthResponse;
import com.yourname.aicommerce.auth.dto.LoginRequest;
import com.yourname.aicommerce.auth.dto.RefreshRequest;
import com.yourname.aicommerce.auth.dto.RegisterRequest;
import com.yourname.aicommerce.common.exception.DuplicateResourceException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Business logic for the authentication lifecycle:
 * register → login → refresh → logout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;

    // ── Register ─────────────────────────────────────────────────────────

    /**
     * Creates a new CUSTOMER account and returns a token pair.
     *
     * @throws DuplicateResourceException if the email is already taken
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .active(true)
                .build();

        userRepository.save(user);
        log.info("Registered new user: {}", user.getEmail());

        return issueTokenPair(user);
    }

    // ── Login ─────────────────────────────────────────────────────────────

    /**
     * Validates credentials via Spring Security's {@code AuthenticationManager}
     * (throws {@code BadCredentialsException} on failure) and returns a token pair.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException if credentials are wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found — this should not happen"));

        log.info("User logged in: {}", user.getEmail());
        return issueTokenPair(user);
    }

    // ── Refresh ───────────────────────────────────────────────────────────

    /**
     * Validates the presented refresh token, revokes it, and issues a new token pair.
     * <p>
     * <strong>Replay attack prevention:</strong> each refresh token is single-use.
     * Once consumed (marked {@code revoked = true}), any subsequent attempt to reuse
     * it — even before the expiry time — will be rejected. An attacker who steals an
     * old refresh token gets 401 as soon as the legitimate owner has already used it.
     *
     * @throws org.springframework.security.core.AuthenticationException on invalid / expired / revoked token
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = jwtService.hashToken(request.getRefreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        if (stored.getRevoked()) {
            // Token reuse detected — revoke ALL tokens for this user as a security response
            log.warn("Refresh token replay detected for user {}; revoking all sessions", stored.getUser().getEmail());
            refreshTokenRepository.revokeAllByUser(stored.getUser());
            throw new InvalidRefreshTokenException("Refresh token has already been used");
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            stored.setRevoked(true);
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        // Rotate: revoke old token, issue new pair
        stored.setRevoked(true);
        User user = stored.getUser();
        log.debug("Rotating refresh token for user {}", user.getEmail());
        return issueTokenPair(user);
    }

    // ── Logout ────────────────────────────────────────────────────────────

    /**
     * Revokes the given refresh token, ending the session.
     * Silently succeeds if the token is already revoked or not found.
     */
    @Transactional
    public void logout(RefreshRequest request) {
        String hash = jwtService.hashToken(request.getRefreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            log.info("Logged out user {}", token.getUser().getEmail());
        });
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = jwtService.generateRefreshTokenRaw();

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(jwtService.hashToken(rawRefresh))
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshTokenExpiryMs() / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefresh)
                .expiresIn(jwtProperties.getAccessTokenExpiryMs() / 1000)
                .build();
    }
}
