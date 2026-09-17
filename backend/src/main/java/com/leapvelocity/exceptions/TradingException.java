package com.leapvelocity.exceptions;

public abstract class TradingException extends RuntimeException {

	protected TradingException(String message) {
		super(message);
	}
}
