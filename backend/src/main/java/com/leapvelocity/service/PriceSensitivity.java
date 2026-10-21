package com.leapvelocity.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared monetary rules for uniform shocks and historical event sensitivities. */
final class PriceSensitivity {
    private PriceSensitivity() { }
    static BigDecimal pnl(BigDecimal marketValue, BigDecimal returnFraction) {
        return marketValue.multiply(returnFraction).setScale(2, RoundingMode.HALF_UP);
    }
    static BigDecimal impact(BigDecimal pnl, BigDecimal equity) {
        return pnl.movePointRight(2).divide(equity, 8, RoundingMode.HALF_UP);
    }
}
