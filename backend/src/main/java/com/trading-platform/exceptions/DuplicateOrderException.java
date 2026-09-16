// package com.leapvelocity.exceptions;

public class DuplicateOrderException extends RuntimeException {

	public DuplicateOrderException(String idempotencyKey) {
		super("Duplicate order idempotency key: " + idempotencyKey);
	}
}
