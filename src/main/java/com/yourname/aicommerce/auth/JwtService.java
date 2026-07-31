package com.yourname.aicommerce.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT utility service — generates, parses, and validates JWTs (HS256),
 * and hashes raw refresh token values for secure storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties props;

    // ── Access token ─────────────────────────────────────────────────────

    /**
     * Generates a signed access token for the given user.
     * Claims: {@code sub} = email, {@code role} = role name.
     */
    public String generateAccessToken(UserDetails user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", extractRole(user))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(props.getAccessTokenExpiryMs())))
                .signWith(signingKey())
                .compact();
    }

    /**
     * Extracts the email (subject) from a JWT without validating expiry.
     * Only use after {@link #isTokenValid} has confirmed the signature.
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Returns true iff the token has a valid signature, has not expired,
     * and its subject matches the given user's username.
     */
    public boolean isTokenValid(String token, UserDetails user) {
        try {
            String email = extractEmail(token);
            return email.equals(user.getUsername()) && !isExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    // ── Refresh token ────────────────────────────────────────────────────

    /**
     * Generates a cryptographically random raw refresh token (UUID v4).
     * The caller is responsible for hashing it before persistence.
     */
    public String generateRefreshTokenRaw() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns the SHA-256 hex digest of the given raw token string.
     * This is the value stored in the {@code refresh_tokens} table.
     */
    public String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec — this cannot happen
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private SecretKey signingKey() {
        byte[] keyBytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }

    private String extractRole(UserDetails user) {
        return user.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("CUSTOMER");
    }
}
