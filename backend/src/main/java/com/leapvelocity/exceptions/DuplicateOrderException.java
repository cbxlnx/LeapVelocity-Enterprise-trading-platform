package com.leapvelocity.exceptions;

public class DuplicateOrderException extends TradingException {

	public DuplicateOrderException(String idempotencyKey) {
		super("Duplicate order idempotency key: " + idempotencyKey);
	}
}
