package com.leapvelocity.exceptions;

public class QuoteNotFoundException extends RuntimeException {

	public QuoteNotFoundException(String symbol) {
		super("No quote available for symbol: " + symbol);
	}
}
