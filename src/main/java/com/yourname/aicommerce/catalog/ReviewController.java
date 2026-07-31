package com.yourname.aicommerce.catalog;

import com.yourname.aicommerce.catalog.dto.ReviewRequest;
import com.yourname.aicommerce.catalog.dto.ReviewResponse;
import com.yourname.aicommerce.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for product reviews.
 * <p>
 * Reviews are scoped to products: {@code /api/v1/products/{productId}/reviews}.
 * The unique constraint (product_id, user_id) ensures one review per user per product.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product review endpoints")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    @Operation(summary = "List reviews for a product (paginated)")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getReviews(
            @PathVariable Long productId,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<ReviewResponse> reviews = reviewService.getReviewsByProduct(productId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved", reviews));
    }

    @GetMapping("/{reviewId}")
    @Operation(summary = "Get a single review by ID")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewById(
            @PathVariable Long productId,
            @PathVariable Long reviewId) {
        ReviewResponse review = reviewService.getReviewById(reviewId);
        return ResponseEntity.ok(ApiResponse.success("Review retrieved", review));
    }

    @PostMapping
    @Operation(summary = "Create a review for a product (one per user)")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @PathVariable Long productId,
            @Valid @RequestBody ReviewRequest request) {
        ReviewResponse review = reviewService.createReview(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(review));
    }

    @PutMapping("/{reviewId}")
    @Operation(summary = "Update an existing review")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewRequest request) {
        ReviewResponse review = reviewService.updateReview(reviewId, request);
        return ResponseEntity.ok(ApiResponse.success("Review updated", review));
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "Delete a review")
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @PathVariable Long productId,
            @PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @GetMapping("/average-rating")
    @Operation(summary = "Get the average rating for a product")
    public ResponseEntity<ApiResponse<Double>> getAverageRating(@PathVariable Long productId) {
        Double avg = reviewService.getAverageRating(productId);
        return ResponseEntity.ok(ApiResponse.success("Average rating", avg));
    }
}
