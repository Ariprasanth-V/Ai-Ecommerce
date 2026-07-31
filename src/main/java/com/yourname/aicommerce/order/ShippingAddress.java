package com.yourname.aicommerce.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * Value object representing a shipping address.
 * <p>
 * Embedded directly into the {@code orders} table as
 * {@code shipping_street}, {@code shipping_city}, etc.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShippingAddress {

    @Column(name = "shipping_street", length = 255)
    private String street;

    @Column(name = "shipping_city", length = 100)
    private String city;

    @Column(name = "shipping_state", length = 100)
    private String state;

    @Column(name = "shipping_zip_code", length = 20)
    private String zipCode;

    @Column(name = "shipping_country", length = 100)
    private String country;
}
