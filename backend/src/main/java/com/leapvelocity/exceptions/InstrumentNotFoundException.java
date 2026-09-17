package com.leapvelocity.exceptions;

public class InstrumentNotFoundException extends TradingException {

	public InstrumentNotFoundException(String symbol) {
		super("Instrument not found: " + symbol);
	}
}
