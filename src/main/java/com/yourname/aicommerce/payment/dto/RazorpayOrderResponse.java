package com.yourname.aicommerce.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RazorpayOrderResponse {
    private Long orderId;
    private String razorpayOrderId;
    private BigDecimal amount;
    private String currency;
    private String keyId;
}
