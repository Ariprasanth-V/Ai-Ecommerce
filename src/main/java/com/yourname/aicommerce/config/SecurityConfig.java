package com.yourname.aicommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourname.aicommerce.auth.CustomUserDetailsService;
import com.yourname.aicommerce.auth.JwtAuthenticationFilter;
import com.yourname.aicommerce.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.LocalDateTime;

/**
 * Stateless JWT security configuration.
 * <p>
 * Public routes: auth endpoints, Swagger UI, OpenAPI docs, and read-only
 * catalog operations. Everything else requires a valid Bearer token.
 * Admin-only write operations are additionally guarded with
 * {@code @PreAuthorize("hasRole('ADMIN')")} at the controller level.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize / @PostAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ── Public auth endpoints ─────────────────────────────────
                .requestMatchers("/api/auth/**").permitAll()
                // ── Swagger / OpenAPI ─────────────────────────────────────
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/v3/api-docs"
                ).permitAll()
                // ── Actuator (health/info only — see application.yml) ─────
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // ── Public read-only catalog ──────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                // ── Orders, Cart & Payments ───────────────────────────────
                .requestMatchers("/api/v1/cart/**").authenticated()
                .requestMatchers("/api/v1/orders/**").authenticated()
                .requestMatchers("/api/v1/payments/webhook").permitAll()
                .requestMatchers("/api/v1/payments/**").authenticated()
                // ── Everything else requires authentication ───────────────
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                    writeError(response, HttpStatus.UNAUTHORIZED,
                        "Authentication required", request.getRequestURI()))
                .accessDeniedHandler((request, response, e) ->
                    writeError(response, HttpStatus.FORBIDDEN,
                        "Access denied — insufficient permissions", request.getRequestURI()))
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String message,
            String path) {
        try {
            response.setStatus(status.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            ErrorResponse body = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(status.value())
                    .error(status.getReasonPhrase())
                    .message(message)
                    .path(path)
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(body));
        } catch (Exception ignored) {
            // If we can't write a response here, there is nothing to do
        }
    }
}
