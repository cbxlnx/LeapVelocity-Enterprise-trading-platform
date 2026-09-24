package com.leapvelocity.entities;

import java.io.Serializable;
import java.util.Objects;

public class PositionId implements Serializable {

    private Long accountId;
    private String symbol;

    public PositionId() {
    }

    public PositionId(Long accountId, String symbol) {
        this.accountId = accountId;
        this.symbol = symbol;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PositionId that)) {
            return false;
        }
        return Objects.equals(accountId, that.accountId) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, symbol);
    }
}
