package com.leapvelocity.service;

import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.enums.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("OrderValidator")
class OrderValidatorTest {

	private final OrderValidator validator = new OrderValidator();

	@Test
	@DisplayName("valid order passes and trims symbol")
	void validOrderPassesAndTrimsSymbol() {
		Order order = new Order(1L, " AAPL ", OrderSide.BUY,
			new BigDecimal("10"), new BigDecimal("150.00"), "order-1");

		validator.validate(order);

		assertEquals("AAPL", order.getSymbol());
	}

	@Test
	@DisplayName("null order throws exception")
	void nullOrderThrowsException() {
		assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
	}

	@Test
	@DisplayName("missing required fields throw exception")
	void missingRequiredFieldsThrowException() {
		assertThrows(IllegalArgumentException.class, () -> validator.validate(
			new Order(null, "AAPL", OrderSide.BUY, BigDecimal.ONE, BigDecimal.ONE, "order-1")));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(
			new Order(1L, "AAPL", null, BigDecimal.ONE, BigDecimal.ONE, "order-1")));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(
			new Order(1L, " ", OrderSide.BUY, BigDecimal.ONE, BigDecimal.ONE, "order-1")));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(
			new Order(1L, "AAPL", OrderSide.BUY, BigDecimal.ONE, BigDecimal.ONE, " ")));
	}

	@Test
	@DisplayName("quantity and price must be positive")
	void quantityAndPriceMustBePositive() {
		assertThrows(IllegalArgumentException.class, () -> validator.validate(
			new Order(1L, "AAPL", OrderSide.BUY, null, BigDecimal.ONE, "order-1")));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(
			new Order(1L, "AAPL", OrderSide.BUY, BigDecimal.ZERO, BigDecimal.ONE, "order-1")));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(
			new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("-1"), BigDecimal.ONE, "order-1")));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(
			new Order(1L, "AAPL", OrderSide.BUY, BigDecimal.ONE, null, "order-1")));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(
			new Order(1L, "AAPL", OrderSide.BUY, BigDecimal.ONE, BigDecimal.ZERO, "order-1")));
		assertThrows(IllegalArgumentException.class, () -> validator.validate(
			new Order(1L, "AAPL", OrderSide.BUY, BigDecimal.ONE, new BigDecimal("-1"), "order-1")));
	}
}
