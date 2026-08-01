package com.yourname.aicommerce.catalog;

import com.yourname.aicommerce.catalog.dto.ProductRequest;
import com.yourname.aicommerce.catalog.dto.ProductResponse;
import com.yourname.aicommerce.common.exception.DuplicateResourceException;
import com.yourname.aicommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Service layer for {@link Product} operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Lists products filtered by search parameters with pagination and sorting.
     */
    public Page<ProductResponse> getProducts(
            Long categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String search,
            Boolean activeOnly,
            Pageable pageable) {

        Specification<Product> spec = ProductSpecification.filterProducts(
                categoryId, minPrice, maxPrice, search, activeOnly);

        return productRepository.findAll(spec, pageable)
                .map(ProductResponse::fromEntity);
    }

    /**
     * Lists all active products with pagination.
     */
    public Page<ProductResponse> getActiveProducts(Pageable pageable) {
        return getProducts(null, null, null, null, true, pageable);
    }

    /**
     * Lists all products (including inactive) with pagination.
     */
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return getProducts(null, null, null, null, false, pageable);
    }

    /**
     * Finds a single product by ID.
     */
    public ProductResponse getProductById(Long id) {
        Product product = findProductOrThrow(id);
        return ProductResponse.fromEntity(product);
    }

    /**
     * Finds a single product by SKU.
     */
    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
        return ProductResponse.fromEntity(product);
    }

    /**
     * Lists products by category with pagination.
     */
    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        return getProducts(categoryId, null, null, null, true, pageable);
    }

    /**
     * Full-text keyword search across name and description.
     */
    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        return getProducts(null, null, null, keyword, true, pageable);
    }

    /**
     * Creates a new product. Fails if the SKU already exists.
     */
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateResourceException("Product", "sku", request.getSku());
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sku(request.getSku())
                .stockQuantity(request.getStockQuantity() != null ? request.getStockQuantity() : 0)
                .imageUrl(request.getImageUrl())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            product.setCategory(category);
        }

        Product saved = productRepository.save(product);
        log.info("Created product: {} (id={}, sku={})", saved.getName(), saved.getId(), saved.getSku());
        return ProductResponse.fromEntity(saved);
    }

    /**
     * Updates an existing product.
     */
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = findProductOrThrow(id);

        // Check for SKU conflict with a different product
        productRepository.findBySku(request.getSku())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Product", "sku", request.getSku());
                });

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setSku(request.getSku());

        if (request.getStockQuantity() != null) {
            product.setStockQuantity(request.getStockQuantity());
        }
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        product.setImageUrl(request.getImageUrl());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        Product saved = productRepository.save(product);
        log.info("Updated product: {} (id={})", saved.getName(), saved.getId());
        return ProductResponse.fromEntity(saved);
    }

    /**
     * Soft-deletes a product by setting active=false.
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProductOrThrow(id);
        product.setActive(false);
        productRepository.save(product);
        log.info("Soft-deleted product: {} (id={})", product.getName(), id);
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }
}
