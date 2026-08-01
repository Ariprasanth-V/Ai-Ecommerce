package com.yourname.aicommerce.order;

import com.yourname.aicommerce.auth.Role;
import com.yourname.aicommerce.auth.User;
import com.yourname.aicommerce.cart.Cart;
import com.yourname.aicommerce.cart.CartItem;
import com.yourname.aicommerce.cart.CartRepository;
import com.yourname.aicommerce.catalog.Product;
import com.yourname.aicommerce.catalog.ProductRepository;
import com.yourname.aicommerce.common.exception.ResourceNotFoundException;
import com.yourname.aicommerce.order.dto.CreateOrderRequest;
import com.yourname.aicommerce.order.dto.OrderResponse;
import com.yourname.aicommerce.order.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Creates an order from user's current cart, validates stock, decrements stock,
     * clears cart, and publishes OrderPlacedEvent AFTER database transaction commits.
     */
    @Transactional
    public OrderResponse createOrder(User user, CreateOrderRequest request) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cart is empty or not found"));

        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Cannot place order with an empty cart");
        }

        // 1. Stock validation for all items
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new IllegalArgumentException(String.format("Product '%s' is no longer available", product.getName()));
            }
            if (cartItem.getQuantity() > product.getStockQuantity()) {
                throw new IllegalArgumentException(String.format(
                        "Insufficient stock for product '%s'. Available: %d, Requested: %d",
                        product.getName(), product.getStockQuantity(), cartItem.getQuantity()));
            }
        }

        // 2. Build Order and OrderItems, decrement stock
        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .shippingAddress(request != null && request.getShippingAddress() != null ? request.getShippingAddress() : new ShippingAddress())
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            // Decrement stock
            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .quantity(cartItem.getQuantity())
                    .unitPrice(product.getPrice())
                    .build();

            order.addItem(orderItem);
            totalAmount = totalAmount.add(orderItem.getSubtotal());
        }

        order.setTotalAmount(totalAmount);
        Order savedOrder = orderRepository.save(order);

        // 3. Clear cart
        cart.getItems().clear();
        cartRepository.save(cart);

        // 4. Build event payload
        List<OrderPlacedEvent.OrderItemEventDto> eventItems = savedOrder.getItems().stream()
                .map(item -> OrderPlacedEvent.OrderItemEventDto.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .build())
                .toList();

        OrderPlacedEvent event = OrderPlacedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .orderId(savedOrder.getId())
                .userId(user.getId())
                .totalAmount(savedOrder.getTotalAmount())
                .items(eventItems)
                .build();

        // 5. Register transaction synchronization to publish to Kafka ONLY AFTER DB commit
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishOrderPlacedEvent(event);
                }
            });
        } else {
            publishOrderPlacedEvent(event);
        }

        log.info("Order #{} placed successfully for user #{}", savedOrder.getId(), user.getId());
        return OrderResponse.fromEntity(savedOrder);
    }

    /**
     * Retrieves paginated order history for the user.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable)
                .map(OrderResponse::fromEntity);
    }

    /**
     * Gets order details by ID — permitted for order owner or ADMIN.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, User currentUser) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (!order.getUser().getId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied — you do not own this order");
        }

        return OrderResponse.fromEntity(order);
    }

    /**
     * Updates order status enforcing state machine rules:
     * PENDING -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED
     * PENDING, CONFIRMED, PROCESSING -> CANCELLED
     */
    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        validateStatusTransition(order.getStatus(), newStatus);

        order.setStatus(newStatus);
        Order updated = orderRepository.save(order);
        log.info("Order #{} status updated from {} to {}", id, order.getStatus(), newStatus);
        return OrderResponse.fromEntity(updated);
    }

    // ── Internal Helpers ─────────────────────────────────────────────────

    private void publishOrderPlacedEvent(OrderPlacedEvent event) {
        try {
            kafkaTemplate.send("order-events", event.getEventId(), event);
            log.info("Published OrderPlacedEvent {} for order #{} to Kafka topic 'order-events'",
                    event.getEventId(), event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to publish OrderPlacedEvent {} to Kafka: {}", event.getEventId(), e.getMessage(), e);
        }
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (current == next) return;

        boolean valid = switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.PROCESSING || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PROCESSING || next == OrderStatus.CANCELLED;
            case PROCESSING -> next == OrderStatus.SHIPPED || next == OrderStatus.CANCELLED;
            case SHIPPED -> next == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED, REFUNDED -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException(String.format(
                    "Invalid order status transition from %s to %s", current, next));
        }
    }
}
