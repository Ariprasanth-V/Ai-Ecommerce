package com.yourname.aicommerce.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPaidEvent {
    private String eventId;
    private Long orderId;
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private BigDecimal amount;
    private LocalDateTime paidAt;
}
