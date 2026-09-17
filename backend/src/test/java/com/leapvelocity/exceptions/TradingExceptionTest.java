package com.leapvelocity.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("Trading Exceptions")
class TradingExceptionTest {

	@Test
	@DisplayName("domain exceptions extend trading exception")
	void domainExceptionsExtendTradingException() {
		assertInstanceOf(TradingException.class, new AccountNotActiveException(1L));
		assertInstanceOf(TradingException.class, new AccountNotFoundException(1L));
		assertInstanceOf(TradingException.class, new DuplicateOrderException("order-1"));
		assertInstanceOf(TradingException.class, new InstrumentNotFoundException("AAPL"));
		assertInstanceOf(TradingException.class,
			new InsufficientFundsException(1L, new BigDecimal("100.00"), new BigDecimal("50.00")));
		assertInstanceOf(TradingException.class,
			new InsufficientHoldingsException(1L, "AAPL", new BigDecimal("10"), new BigDecimal("5")));
	}
}
