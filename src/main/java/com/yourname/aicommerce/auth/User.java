package com.yourname.aicommerce.auth;

import com.yourname.aicommerce.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Application user — customer or admin.
 * <p>
 * Implements {@link UserDetails} so Spring Security's {@code DaoAuthenticationProvider}
 * can use this entity directly without an adapter wrapper. The {@code active} flag
 * maps to {@link #isEnabled()}, enabling soft-delete style account suspension.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity implements UserDetails {

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

    // ── UserDetails implementation ────────────────────────────────────────

    /**
     * Returns a singleton list containing the user's role prefixed with "ROLE_",
     * e.g. {@code ROLE_CUSTOMER} or {@code ROLE_ADMIN}.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Spring Security username is the user's email address. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** A deactivated account cannot authenticate. */
    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(active);
    }
}
