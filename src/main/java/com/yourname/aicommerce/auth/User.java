package com.yourname.aicommerce.auth;

import com.yourname.aicommerce.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Application user — customer or admin.
 * <p>
 * The {@code password} field will store a BCrypt hash once the
 * authentication module is implemented.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.CUSTOMER;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
