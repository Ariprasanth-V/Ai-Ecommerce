-- ============================================================
-- V4 — Refresh token storage for JWT rotation
-- ============================================================
-- Stores only the SHA-256 hex digest of the raw refresh token.
-- The raw token is returned once to the client and never stored.
-- ============================================================

CREATE TABLE refresh_tokens (
    id          BIGSERIAL       PRIMARY KEY,
    token_hash  VARCHAR(64)     NOT NULL UNIQUE,   -- SHA-256 hex (64 chars)
    user_id     BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMP       NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_token_user ON refresh_tokens(user_id);
