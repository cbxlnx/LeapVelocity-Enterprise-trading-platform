//package com.leapvelocity.exceptions;

import java.math.BigDecimal;

public class InsufficientHoldingsException extends RuntimeException {

	public InsufficientHoldingsException(Long accountId, String symbol, BigDecimal requestedQuantity, BigDecimal availableQuantity) {
		super("Insufficient holdings for account " + accountId
				+ " and symbol " + symbol
				+ ": requested=" + requestedQuantity
				+ ", available=" + availableQuantity);
	}
}
