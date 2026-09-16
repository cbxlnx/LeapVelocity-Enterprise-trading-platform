// package com.leapvelocity.exceptions;

public class AccountNotActiveException extends RuntimeException {

	public AccountNotActiveException(Long accountId) {
		super("Account is not active: " + accountId);
	}

	public AccountNotActiveException(String message) {
		super(message);
	}
}
