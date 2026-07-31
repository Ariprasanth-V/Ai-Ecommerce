package com.yourname.aicommerce.catalog;

import com.yourname.aicommerce.catalog.dto.CategoryRequest;
import com.yourname.aicommerce.catalog.dto.CategoryResponse;
import com.yourname.aicommerce.common.exception.DuplicateResourceException;
import com.yourname.aicommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for {@link Category} operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Returns all root-level categories (those without a parent).
     */
    public List<CategoryResponse> getRootCategories() {
        return categoryRepository.findByParentCategoryIsNull().stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    /**
     * Returns all categories.
     */
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    /**
     * Finds a single category by ID.
     */
    public CategoryResponse getCategoryById(Long id) {
        Category category = findCategoryOrThrow(id);
        return CategoryResponse.fromEntity(category);
    }

    /**
     * Creates a new category. Fails if the name is already taken.
     */
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Category", "name", request.getName());
        }

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .build();

        if (request.getParentId() != null) {
            Category parent = findCategoryOrThrow(request.getParentId());
            category.setParentCategory(parent);
        }

        Category saved = categoryRepository.save(category);
        log.info("Created category: {} (id={})", saved.getName(), saved.getId());
        return CategoryResponse.fromEntity(saved);
    }

    /**
     * Updates an existing category.
     */
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findCategoryOrThrow(id);

        // Check for name conflict with a different category
        categoryRepository.findByNameIgnoreCase(request.getName())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Category", "name", request.getName());
                });

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());

        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new IllegalArgumentException("A category cannot be its own parent");
            }
            Category parent = findCategoryOrThrow(request.getParentId());
            category.setParentCategory(parent);
        } else {
            category.setParentCategory(null);
        }

        Category saved = categoryRepository.save(category);
        log.info("Updated category: {} (id={})", saved.getName(), saved.getId());
        return CategoryResponse.fromEntity(saved);
    }

    /**
     * Deletes a category by ID. Cascades to sub-categories and products.
     */
    @Transactional
    public void deleteCategory(Long id) {
        Category category = findCategoryOrThrow(id);
        categoryRepository.delete(category);
        log.info("Deleted category: {} (id={})", category.getName(), id);
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private Category findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }
}
