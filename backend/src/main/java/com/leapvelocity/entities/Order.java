package com.leapvelocity.entities;

import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// customer trade order
@Entity
@Table(name = "orders")
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;                           // unique order identifier
    
    @Column(name = "account_id", nullable = false)
    private Long accountId;                    // account placing the order
    
    @Column(nullable = false)
    private String symbol;                     // instrument ticker
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;                    // buy or sell
    
    @Column(nullable = false)
    private BigDecimal quantity;               // number of shares
    
    @Column(nullable = false)
    private BigDecimal price;                  // price per share
    
    @Column(nullable = false)
    private OrderStatus status;                // new, filled, rejected, cancelled
    
    @Column(name = "indempotency_key", nullable = false, unique = true)
    private String idempotencyKey;             // prevents duplicate orders
    
    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;           // order submission timestamp
    
    // relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private Account account;                   // account that placed this order
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symbol", referencedColumnName = "symbol", insertable = false, updatable = false)
    private Instrument instrument;             // instrument being traded
    
    // Constructors
    public Order() {
    }
    
    public Order(Long accountId, String symbol, OrderSide side, BigDecimal quantity, BigDecimal price, String idempotencyKey) {
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.status = OrderStatus.NEW;
        this.idempotencyKey = idempotencyKey;
        this.createdOn = LocalDateTime.now();
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
    
    public OrderSide getSide() {
        return side;
    }
    
    public void setSide(OrderSide side) {
        this.side = side;
    }
    
    public BigDecimal getQuantity() {
        return quantity;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public OrderStatus getStatus() {
        return status;
    }
    
    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    
    public String getIdempotencyKey() {
        return idempotencyKey;
    }
    
    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
    
    public LocalDateTime getCreatedOn() {
        return createdOn;
    }
    
    public void setCreatedOn(LocalDateTime createdOn) {
        this.createdOn = createdOn;
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
