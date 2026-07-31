package com.yourname.aicommerce.order;

import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a customer order.
 * <p>
 * Contains embedded {@link ShippingAddress} and a snapshot of the total
 * amount at the time the order was placed. Individual line-item prices
 * are captured in {@link OrderItem}.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Embedded
    @Builder.Default
    private ShippingAddress shippingAddress = new ShippingAddress();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    // ── Convenience helpers ──────────────────────────────────────────────

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
