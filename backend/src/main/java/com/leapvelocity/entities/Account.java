package com.leapvelocity.entities;

import com.leapvelocity.entities.enums.AccountStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// customer trading account
@Entity
@Table(name = "accounts")
public class Account {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                           // unique database identifier
    
    @Column(name = "account_id", nullable = false, unique = true)
    private String accountId;                  // external account identifier
    
    @Column(name = "holder_name", nullable = false)
    private String holderName;                 // account owner name
    
    @Column(name = "cash_balance", nullable = false)
    private BigDecimal cashBalance;            // available cash
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;              // active, suspended, or closed
    
    @Column(nullable = false)
    private Long version;                      // for optimistic locking
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;         // last modification timestamp
    
    // Constructors
    public Account() {
    }
    
    public Account(String accountId, String holderName, BigDecimal cashBalance, AccountStatus status) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.cashBalance = cashBalance;
        this.status = status;
        this.version = 0L;
        this.lastUpdated = LocalDateTime.now();
    }
    
    // deduct cash from account
    public void debit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (this.cashBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient cash balance");
        }
        this.cashBalance = this.cashBalance.subtract(amount);
        this.lastUpdated = LocalDateTime.now();
    }
    
    // add cash to account
    public void credit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.cashBalance = this.cashBalance.add(amount);
        this.lastUpdated = LocalDateTime.now();
    }
    
    // check if account is active
    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public String getHolderName() {
        return holderName;
    }
    
    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }
    
    public BigDecimal getCashBalance() {
        return cashBalance;
    }
    
    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }
    
    public AccountStatus getStatus() {
        return status;
    }
    
    public void setStatus(AccountStatus status) {
        this.status = status;
    }
    
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
    
    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
