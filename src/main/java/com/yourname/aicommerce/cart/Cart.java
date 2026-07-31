package com.yourname.aicommerce.cart;

import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Shopping cart — one per user.
 * <p>
 * Uses a {@code @OneToOne} relationship with {@link User} enforced by
 * a unique constraint on {@code user_id} in the database.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CartItem> items = new ArrayList<>();

    // ── Convenience helpers ──────────────────────────────────────────────

    /**
     * Adds an item to the cart. If the product is already in the cart,
     * increments the quantity instead.
     */
    public void addItem(CartItem item) {
        items.stream()
                .filter(existing -> existing.getProduct().getId().equals(item.getProduct().getId()))
                .findFirst()
                .ifPresentOrElse(
                        existing -> existing.setQuantity(existing.getQuantity() + item.getQuantity()),
                        () -> {
                            item.setCart(this);
                            items.add(item);
                        }
                );
    }

    public void removeItem(CartItem item) {
        items.remove(item);
        item.setCart(null);
    }

    public void clear() {
        items.clear();
    }
}
