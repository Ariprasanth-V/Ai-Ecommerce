-- ============================================================
-- V1 — Baseline schema for AI-Commerce
-- ============================================================
-- Uses CREATE TABLE IF NOT EXISTS so this migration is safe
-- against tables already created by Hibernate ddl-auto: update
-- (categories, products, reviews).
-- ============================================================

-- 1. users
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL       PRIMARY KEY,
    first_name  VARCHAR(50)     NOT NULL,
    last_name   VARCHAR(50)     NOT NULL,
    email       VARCHAR(150)    NOT NULL UNIQUE,
    password    VARCHAR(255)    NOT NULL,
    role        VARCHAR(20)     NOT NULL DEFAULT 'CUSTOMER',
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- 2. categories (self-referencing hierarchy)
CREATE TABLE IF NOT EXISTS categories (
    id          BIGSERIAL       PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL UNIQUE,
    description VARCHAR(500),
    image_url   VARCHAR(255),
    parent_id   BIGINT          REFERENCES categories(id),
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- 3. products
CREATE TABLE IF NOT EXISTS products (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(200)    NOT NULL,
    description     VARCHAR(2000),
    price           DECIMAL(10,2)   NOT NULL,
    sku             VARCHAR(50)     NOT NULL UNIQUE,
    stock_quantity  INTEGER         NOT NULL DEFAULT 0,
    image_url       VARCHAR(255),
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    category_id     BIGINT          REFERENCES categories(id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- 4. reviews
CREATE TABLE IF NOT EXISTS reviews (
    id          BIGSERIAL       PRIMARY KEY,
    rating      INTEGER         NOT NULL,
    comment     VARCHAR(1000),
    user_id     BIGINT          NOT NULL,
    product_id  BIGINT          NOT NULL REFERENCES products(id),
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- 5. carts (one per user)
CREATE TABLE IF NOT EXISTS carts (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     BIGINT          NOT NULL UNIQUE REFERENCES users(id),
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- 6. cart_items (one line per product per cart)
CREATE TABLE IF NOT EXISTS cart_items (
    id          BIGSERIAL       PRIMARY KEY,
    cart_id     BIGINT          NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id  BIGINT          NOT NULL REFERENCES products(id),
    quantity    INTEGER         NOT NULL DEFAULT 1,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT  uk_cart_item_cart_product UNIQUE (cart_id, product_id)
);

-- 7. orders
CREATE TABLE IF NOT EXISTS orders (
    id                  BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES users(id),
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    total_amount        DECIMAL(12,2)   NOT NULL,
    shipping_street     VARCHAR(255),
    shipping_city       VARCHAR(100),
    shipping_state      VARCHAR(100),
    shipping_zip_code   VARCHAR(20),
    shipping_country    VARCHAR(100),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- 8. order_items (snapshot of price at order time)
CREATE TABLE IF NOT EXISTS order_items (
    id          BIGSERIAL       PRIMARY KEY,
    order_id    BIGINT          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  BIGINT          NOT NULL REFERENCES products(id),
    quantity    INTEGER         NOT NULL,
    unit_price  DECIMAL(10,2)   NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
