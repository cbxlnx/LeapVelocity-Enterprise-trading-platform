package com.leapvelocity.exceptions;

public class AccountNotFoundException extends RuntimeException {

	public AccountNotFoundException(Long accountId) {
		super("Account not found: " + accountId);
	}

	public AccountNotFoundException(String message) {
		super(message);
	}
}
