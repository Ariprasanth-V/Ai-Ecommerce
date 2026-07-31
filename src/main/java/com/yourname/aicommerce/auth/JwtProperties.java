package com.yourname.aicommerce.auth;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Strongly-typed binding for the {@code app.jwt.*} configuration block.
 * <p>
 * Values are sourced from {@code application.yml} and ultimately from
 * environment variables (or a local {@code .env} file via spring-dotenv).
 * <p>
 * {@code JWT_SECRET} has <strong>no fallback</strong> — the application
 * fails immediately at startup with a clear error message if:
 * <ul>
 *   <li>The variable is missing (secret is null or blank)</li>
 *   <li>The secret decodes to fewer than 32 bytes (256 bits), which is the
 *       minimum key length required by HS256</li>
 * </ul>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Base64-encoded HS256 secret key — must decode to at least 32 bytes (256 bits). */
    private String secret;

    /** Access token lifetime in milliseconds (default 15 min = 900_000 ms). */
    private long accessTokenExpiryMs;

    /** Refresh token lifetime in milliseconds (default 7 days = 604_800_000 ms). */
    private long refreshTokenExpiryMs;

    /**
     * Validates the JWT secret at application startup.
     * <p>
     * Runs after the properties are bound but before the app finishes starting,
     * so any misconfiguration surfaces immediately as a clear
     * {@link IllegalStateException} rather than a cryptic JJWT
     * {@code WeakKeyException} or NullPointerException at request time.
     *
     * @throws IllegalStateException if the secret is missing, still an unresolved
     *                               placeholder, or too short for HS256
     */
    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is not set. " +
                    "Add it to your .env file or set it as a system environment variable. " +
                    "Generate a value with: " +
                    "$bytes = New-Object byte[] 32; " +
                    "[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); " +
                    "[Convert]::ToBase64String($bytes)");
        }

        // Detect an unresolved Spring placeholder (e.g., ${JWT_SECRET} was never substituted)
        if (secret.startsWith("${") && secret.endsWith("}")) {
            throw new IllegalStateException(
                    "JWT_SECRET is still an unresolved placeholder: '" + secret + "'. " +
                    "The environment variable is not being picked up. " +
                    "Ensure your .env file exists in the project root and spring-dotenv is on the classpath, " +
                    "or set JWT_SECRET as a system environment variable.");
        }

        // Validate minimum key length: HS256 requires >= 256 bits = 32 bytes
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "JWT_SECRET is not valid Base64. " +
                    "Regenerate it using the PowerShell command in the README.", e);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(String.format(
                    "JWT_SECRET decodes to only %d bytes but HS256 requires at least 32 bytes (256 bits). " +
                    "Regenerate a longer secret.", keyBytes.length));
        }
    }
}
