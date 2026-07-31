-- ============================================================
-- V3 — Performance indexes
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_product_sku        ON products    (sku);
CREATE INDEX IF NOT EXISTS idx_product_category   ON products    (category_id);
CREATE INDEX IF NOT EXISTS idx_review_product     ON reviews     (product_id);
CREATE INDEX IF NOT EXISTS idx_review_user        ON reviews     (user_id);
CREATE INDEX IF NOT EXISTS idx_cart_user          ON carts       (user_id);
CREATE INDEX IF NOT EXISTS idx_order_user         ON orders      (user_id);
CREATE INDEX IF NOT EXISTS idx_order_status       ON orders      (status);
CREATE INDEX IF NOT EXISTS idx_order_item_order   ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_cart_item_cart     ON cart_items  (cart_id);
