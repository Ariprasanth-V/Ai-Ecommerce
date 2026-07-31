package com.yourname.aicommerce.catalog;

import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * A product review submitted by a user.
 * <p>
 * Enforces a <strong>unique constraint on (product_id, user_id)</strong> so
 * each user may submit at most one review per product. Attempting a second
 * review will result in a constraint violation.
 */
@Entity
@Table(
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_review_product_user",
                columnNames = {"product_id", "user_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    /**
     * Star rating from 1 to 5.
     */
    @Column(nullable = false)
    private Integer rating;

    @Column(length = 1000)
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}
