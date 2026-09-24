package com.leapvelocity.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

// account holding in a specific instrument
@Entity
@Table(name = "positions")
@IdClass(PositionId.class)
public class Position {
    
    @Transient
    private Long id;                           // unique position identifier
    
    @Id
    @Column(name = "account_id", nullable = false)
    private Long accountId;                    // account owning this position
    
    @Id
    @Column(nullable = false)
    private String symbol;                     // instrument ticker
    
    @Column(nullable = false)
    private BigDecimal quantity;               // number of shares held
    
    @Column(name = "average_cost", nullable = false)
    private BigDecimal averageCost;            // average acquisition cost per share
    
    // relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private Account account;                   // account holding this position
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", referencedColumnName = "symbol", insertable = false, updatable = false)
    private Instrument instrument;             // instrument held
    
    // Constructors
    public Position() {
    }
    
    public Position(Long accountId, String symbol, BigDecimal quantity, BigDecimal averageCost) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.averageCost = averageCost;
    }
    
    // update position with new trade (recalculates average cost)
    public void apply(BigDecimal quantity, BigDecimal price) {
        if (quantity == null || price == null) {
            throw new IllegalArgumentException("Quantity and price cannot be null");
        }

        // current holdings total cost
        BigDecimal currentValue = this.averageCost.multiply(this.quantity);
        // new trade's total cost
        BigDecimal newValue = price.multiply(quantity);

        // new average cost after incorporating the new trade
        // new average = (old total + new total) / (old qty + new qty)
        this.quantity = this.quantity.add(quantity);
        this.averageCost = currentValue.add(newValue).divide(this.quantity, RoundingMode.HALF_UP);
    }
    
    // calculate position value at current market price
    public BigDecimal marketValue(BigDecimal currentPrice) {
        if (currentPrice == null) {
            throw new IllegalArgumentException("Current price cannot be null");
        }
        return this.quantity.multiply(currentPrice);
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
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
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getAverageCost() {
        return averageCost;
    }
    
    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }
    
    public Account getAccount() {
        return account;
    }
    
    public void setAccount(Account account) {
        this.account = account;
    }
    
    public Instrument getInstrument() {
        return instrument;
    }
    
    public void setInstrument(Instrument instrument) {
        this.instrument = instrument;
    }
}
