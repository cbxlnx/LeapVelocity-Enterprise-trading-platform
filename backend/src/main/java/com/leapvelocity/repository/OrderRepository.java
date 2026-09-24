package com.leapvelocity.repository;

import com.leapvelocity.entities.Order;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}