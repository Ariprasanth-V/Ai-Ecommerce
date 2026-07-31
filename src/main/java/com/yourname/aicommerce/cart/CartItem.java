package com.yourname.aicommerce.cart;

import com.yourname.aicommerce.catalog.Product;
import com.yourname.aicommerce.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A single line item in a shopping cart.
 * <p>
 * The unique constraint {@code (cart_id, product_id)} ensures that
 * each product appears at most once per cart — quantity is incremented
 * instead of adding duplicate rows.
 */
@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_item_cart_product",
                columnNames = {"cart_id", "product_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;
}
