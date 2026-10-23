package com.leapvelocity.exceptions;

public class InvalidQuoteRequestException extends RuntimeException {

	public InvalidQuoteRequestException(String message) {
		super(message);
	}
}
