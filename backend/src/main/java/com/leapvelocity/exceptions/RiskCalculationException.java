package com.leapvelocity.exceptions;

/** Operational failure, never a caller validation error or an invented unavailable metric. */
public final class RiskCalculationException extends RuntimeException {
    public enum Kind { TIMEOUT, UNAVAILABLE, INVALID_RESPONSE }
    private final Kind kind;
    public RiskCalculationException(Kind kind, Throwable cause) {
        super("Risk calculation service failure: " + kind, cause);
        this.kind = kind;
    }
    public Kind kind() { return kind; }
}
