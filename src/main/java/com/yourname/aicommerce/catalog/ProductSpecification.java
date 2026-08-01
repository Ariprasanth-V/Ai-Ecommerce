package com.yourname.aicommerce.catalog;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * Specifications for filtering {@link Product} queries dynamically.
 */
public class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> filterProducts(
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String search,
            Boolean activeOnly) {

        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();

            if (Boolean.TRUE.equals(activeOnly)) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("active"), true));
            }

            if (categoryId != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("category").get("id"), categoryId));
            }

            if (minPrice != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
            }

            if (maxPrice != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                var nameLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern);
                var descLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern);
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.or(nameLike, descLike));
            }

            return predicate;
        };
    }
}
