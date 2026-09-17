package com.leapvelocity.exceptions;

public class AccountNotActiveException extends TradingException {

	public AccountNotActiveException(Long accountId) {
		super("Account is not active: " + accountId);
	}

	public AccountNotActiveException(String message) {
		super(message);
	}
}
