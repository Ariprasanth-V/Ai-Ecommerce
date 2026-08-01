package com.yourname.aicommerce.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @NonNull
    @Override
    @EntityGraph(attributePaths = {"items", "items.product", "user"})
    Optional<Order> findById(@NonNull Long id);
}
