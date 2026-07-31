package com.yourname.aicommerce.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Category} entities.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    List<Category> findByParentCategoryIsNull();

    List<Category> findByParentCategoryId(Long parentId);

    boolean existsByNameIgnoreCase(String name);
}
