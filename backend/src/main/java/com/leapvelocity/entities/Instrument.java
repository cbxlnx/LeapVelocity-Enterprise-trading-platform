package com.leapvelocity.entities;

import jakarta.persistence.*;

// represents a tradable financial instrument (stock, etf, etc.)
@Entity
@Table(name = "instruments")
public class Instrument {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                           // unique identifier
    
    @Column(nullable = false, unique = true)
    private String symbol;                     // ticker symbol (AAPL, GOOGL, etc.)
    
    @Column(nullable = false)
    private String name;                       // full name
    
    @Column(name = "asset_class", nullable = false)
    private String assetClass;                 // equity, etf, etc.
    
    @Column(nullable = false)
    private String currency;                   // trading currency
    
    @Column(nullable = false)
    private boolean tradable;                  // whether instrument can be traded
    
    // Constructors
    public Instrument() {
    }
    
    public Instrument(String symbol, String name, String assetClass, String currency, boolean tradable) {
        this.symbol = symbol;
        this.name = name;
        this.assetClass = assetClass;
        this.currency = currency;
        this.tradable = tradable;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getAssetClass() {
        return assetClass;
    }
    
    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public boolean isTradable() {
        return tradable;
    }
    
    public void setTradable(boolean tradable) {
        this.tradable = tradable;
    }
}
