package com.yourname.aicommerce.order;

import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.common.response.ApiResponse;
import com.yourname.aicommerce.order.dto.CreateOrderRequest;
import com.yourname.aicommerce.order.dto.OrderResponse;
import com.yourname.aicommerce.order.dto.UpdateOrderStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Order management operations.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order placement and history endpoints")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place a new order from current user's cart")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody(required = false) CreateOrderRequest request) {
        OrderResponse order = orderService.createOrder(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(order));
    }

    @GetMapping
    @Operation(summary = "Get paginated order history for current user")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getUserOrders(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<OrderResponse> orders = orderService.getUserOrders(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("User orders retrieved", orders));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order details by ID (owner or ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        OrderResponse order = orderService.getOrderById(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved", order));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update order status — ADMIN only")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderResponse order = orderService.updateOrderStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success("Order status updated", order));
    }
}
