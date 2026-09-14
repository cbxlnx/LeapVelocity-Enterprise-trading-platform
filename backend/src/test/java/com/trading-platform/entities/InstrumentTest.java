package com.leapvelocity.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Instrument")
class InstrumentTest {

    @Test
    @DisplayName("constructor stores instrument details")
    void constructorStoresInstrumentDetails() {
        Instrument instrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);

        assertEquals("AAPL", instrument.getSymbol());
        assertEquals("Apple Inc.", instrument.getName());
        assertEquals("EQUITY", instrument.getAssetClass());
        assertEquals("USD", instrument.getCurrency());
        assertTrue(instrument.isTradable());
        assertTrue(instrument.isTradeble());
    }

    @Test
    @DisplayName("tradable flag can be changed")
    void tradableFlagCanBeChanged() {
        Instrument instrument = new Instrument("VWRL", "Vanguard FTSE All-World", "ETF", "GBP", false);

        assertFalse(instrument.isTradable());

        instrument.setTradable(true);

        assertTrue(instrument.isTradable());
    }
}
