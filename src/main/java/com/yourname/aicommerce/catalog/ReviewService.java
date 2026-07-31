package com.yourname.aicommerce.catalog;

import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.auth.UserRepository;
import com.yourname.aicommerce.catalog.dto.ReviewRequest;
import com.yourname.aicommerce.catalog.dto.ReviewResponse;
import com.yourname.aicommerce.common.exception.DuplicateResourceException;
import com.yourname.aicommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for {@link Review} operations.
 * <p>
 * Enforces the one-review-per-user-per-product business rule at the
 * application level (backed by a DB unique constraint).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Returns paginated reviews for a product.
     */
    public Page<ReviewResponse> getReviewsByProduct(Long productId, Pageable pageable) {
        return reviewRepository.findByProductId(productId, pageable)
                .map(ReviewResponse::fromEntity);
    }

    /**
     * Returns paginated reviews by a specific user.
     */
    public Page<ReviewResponse> getReviewsByUser(Long userId, Pageable pageable) {
        return reviewRepository.findByUser_Id(userId, pageable)
                .map(ReviewResponse::fromEntity);
    }

    /**
     * Finds a single review by ID.
     */
    public ReviewResponse getReviewById(Long id) {
        Review review = findReviewOrThrow(id);
        return ReviewResponse.fromEntity(review);
    }

    /**
     * Creates a review for a product. Throws {@link DuplicateResourceException}
     * if the user has already reviewed this product.
     */
    @Transactional
    public ReviewResponse createReview(Long productId, ReviewRequest request) {
        // Verify product exists
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        // Verify user exists
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getUserId()));

        // Enforce one review per user per product
        if (reviewRepository.existsByProductIdAndUser_Id(productId, request.getUserId())) {
            throw new DuplicateResourceException(
                    "User " + request.getUserId() + " has already reviewed product " + productId);
        }

        Review review = Review.builder()
                .rating(request.getRating())
                .comment(request.getComment())
                .user(user)
                .product(product)
                .build();

        Review saved = reviewRepository.save(review);
        log.info("Created review id={} for product {} by user {}", saved.getId(), productId, request.getUserId());
        return ReviewResponse.fromEntity(saved);
    }

    /**
     * Updates an existing review (only rating and comment can change).
     */
    @Transactional
    public ReviewResponse updateReview(Long id, ReviewRequest request) {
        Review review = findReviewOrThrow(id);

        review.setRating(request.getRating());
        review.setComment(request.getComment());

        Review saved = reviewRepository.save(review);
        log.info("Updated review id={}", saved.getId());
        return ReviewResponse.fromEntity(saved);
    }

    /**
     * Deletes a review by ID.
     */
    @Transactional
    public void deleteReview(Long id) {
        Review review = findReviewOrThrow(id);
        reviewRepository.delete(review);
        log.info("Deleted review id={}", id);
    }

    /**
     * Returns the average rating for a product.
     */
    public Double getAverageRating(Long productId) {
        Double avg = reviewRepository.findAverageRatingByProductId(productId);
        return avg != null ? avg : 0.0;
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    private Review findReviewOrThrow(Long id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", id));
    }
}
