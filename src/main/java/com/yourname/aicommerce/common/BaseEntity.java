package com.yourname.aicommerce.common;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Mapped superclass providing common audit fields for all entities.
 * <p>
 * Every entity inheriting from this will automatically receive:
 * <ul>
 *   <li>{@code id} — auto-generated primary key</li>
 *   <li>{@code createdAt} — timestamp set on first persist</li>
 *   <li>{@code updatedAt} — timestamp updated on every modification</li>
 * </ul>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
