package com.leapvelocity.repository.inmemory;

import com.leapvelocity.entities.Order;
import com.leapvelocity.repository.OrderRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryOrderRepository implements OrderRepository {

	private final Map<String, Order> ordersByIdempotencyKey;

	public InMemoryOrderRepository() {
		this.ordersByIdempotencyKey = new HashMap<>();
	}

	@Override
	public boolean existsByIdempotencyKey(String idempotencyKey) {
		return ordersByIdempotencyKey.containsKey(idempotencyKey);
	}

	@Override
	public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
		return Optional.ofNullable(ordersByIdempotencyKey.get(idempotencyKey));
	}

	@Override
	public void save(Order order) {
		ordersByIdempotencyKey.put(order.getIdempotencyKey(), order);
	}
}
