package com.yourname.aicommerce.order.dto;

import com.yourname.aicommerce.order.ShippingAddress;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating an order from current user's cart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @Valid
    private ShippingAddress shippingAddress;
}
