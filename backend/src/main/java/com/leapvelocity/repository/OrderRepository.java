package com.leapvelocity.repository;

import com.leapvelocity.entities.Order;

import java.util.Optional;

public interface OrderRepository {

	boolean existsByIdempotencyKey(String idempotencyKey);

	Optional<Order> findByIdempotencyKey(String idempotencyKey);

	void save(Order order);
}
