package com.yourname.aicommerce.catalog.dto;

import com.yourname.aicommerce.catalog.Review;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Response DTO for review data returned to clients.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {

    private Long id;
    private Integer rating;
    private String comment;
    private Long userId;
    private String userFullName;
    private Long productId;
    private String productName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Mapper ───────────────────────────────────────────────────────────

    public static ReviewResponse fromEntity(Review review) {
        ReviewResponseBuilder builder = ReviewResponse.builder()
                .id(review.getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt());

        if (review.getUser() != null) {
            builder.userId(review.getUser().getId());
            builder.userFullName(
                    review.getUser().getFirstName() + " " + review.getUser().getLastName());
        }

        if (review.getProduct() != null) {
            builder.productId(review.getProduct().getId());
            builder.productName(review.getProduct().getName());
        }

        return builder.build();
    }
}
