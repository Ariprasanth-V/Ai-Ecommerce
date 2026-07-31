-- ============================================================
-- V2 — Add unique constraint + FK on reviews(product_id, user_id)
-- ============================================================
-- The reviews table may have been created by Hibernate ddl-auto
-- before the users table existed, so user_id had no FK.
-- This migration:
--   1. Deletes orphan reviews whose user_id doesn't exist in users
--   2. Adds the FK from reviews.user_id → users.id
--   3. Adds the unique constraint (one review per user per product)
-- ============================================================

-- 1. Delete any review rows whose user_id has no matching users row
DELETE FROM reviews
WHERE user_id NOT IN (SELECT id FROM users);

-- 2. Add FK on reviews.user_id → users.id (if not already present)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_review_user'
    ) THEN
        ALTER TABLE reviews
            ADD CONSTRAINT fk_review_user
            FOREIGN KEY (user_id) REFERENCES users(id);
    END IF;
END $$;

-- 3. Add unique constraint on (product_id, user_id)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uk_review_product_user'
    ) THEN
        ALTER TABLE reviews
            ADD CONSTRAINT uk_review_product_user
            UNIQUE (product_id, user_id);
    END IF;
END $$;
