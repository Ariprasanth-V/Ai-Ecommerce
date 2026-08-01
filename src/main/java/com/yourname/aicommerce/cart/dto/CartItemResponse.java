package com.yourname.aicommerce.cart.dto;

import com.yourname.aicommerce.cart.CartItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO representing a line item in the shopping cart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {

    private Long id;
    private Long productId;
    private String productName;
    private BigDecimal productPrice;
    private String productImageUrl;
    private Integer quantity;
    private BigDecimal subtotal;

    public static CartItemResponse fromEntity(CartItem item) {
        BigDecimal livePrice = item.getProduct().getPrice();
        BigDecimal itemSubtotal = livePrice.multiply(BigDecimal.valueOf(item.getQuantity()));

        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productPrice(livePrice)
                .productImageUrl(item.getProduct().getImageUrl())
                .quantity(item.getQuantity())
                .subtotal(itemSubtotal)
                .build();
    }
}
