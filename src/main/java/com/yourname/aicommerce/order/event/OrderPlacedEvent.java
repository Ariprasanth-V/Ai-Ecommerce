package com.yourname.aicommerce.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Domain event emitted when an order is successfully placed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacedEvent {

    private String eventId;
    private Long orderId;
    private Long userId;
    private BigDecimal totalAmount;
    private List<OrderItemEventDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemEventDto {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
