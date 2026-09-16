//package com.leapvelocity.exceptions;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

	public InsufficientFundsException(Long accountId, BigDecimal requiredAmount, BigDecimal availableCash) {
		super("Insufficient funds for account " + accountId
				+ ": required=" + requiredAmount
				+ ", available=" + availableCash);
	}
}
