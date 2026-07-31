package com.yourname.aicommerce.catalog.dto;

import com.yourname.aicommerce.catalog.Product;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for product data returned to clients.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String sku;
    private Integer stockQuantity;
    private boolean inStock;
    private String imageUrl;
    private Boolean active;
    private Long categoryId;
    private String categoryName;
    private double averageRating;
    private int reviewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Mapper ───────────────────────────────────────────────────────────

    public static ProductResponse fromEntity(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .sku(product.getSku())
                .stockQuantity(product.getStockQuantity())
                .inStock(product.isInStock())
                .imageUrl(product.getImageUrl())
                .active(product.getActive())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviews() != null ? product.getReviews().size() : 0)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
