package com.yourname.aicommerce.catalog.dto;

import com.yourname.aicommerce.catalog.Category;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for category data returned to clients.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private Long parentId;
    private String parentName;
    private int productCount;
    private List<CategorySummary> subCategories;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Minimal sub-category view to avoid deeply nested recursive serialization.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategorySummary {
        private Long id;
        private String name;
    }

    // ── Mapper ───────────────────────────────────────────────────────────

    public static CategoryResponse fromEntity(Category category) {
        List<CategorySummary> subs = category.getSubCategories() == null
                ? List.of()
                : category.getSubCategories().stream()
                .map(c -> CategorySummary.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .build())
                .toList();

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .parentId(category.getParentCategory() != null ? category.getParentCategory().getId() : null)
                .parentName(category.getParentCategory() != null ? category.getParentCategory().getName() : null)
                .productCount(category.getProducts() != null ? category.getProducts().size() : 0)
                .subCategories(subs)
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
