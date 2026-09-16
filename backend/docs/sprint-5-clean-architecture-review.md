# Sprint 5 Clean Architecture Review And Sprint 6 Preparation

## Purpose

This document groups the remaining clean code, SOLID, and clean architecture improvements into separate tasks. The goal is to make the Sprint 5 Java skeleton ready for Sprint 6, where the project will move into Spring Boot, REST API design, MyBatis persistence, and layered architecture.

Sprint 5 should stay focused on the Java business engine. Do not overbuild Spring-style infrastructure too early, but prepare the code so Sprint 6 can add controllers, repositories, persistence, and exception handling cleanly.

## Current Sprint 5 Status

The backend already has the main Sprint 5 skeleton:

- Domain entities: `Account`, `Instrument`, `Order`, `Position`
- Enums: `AccountStatus`, `OrderSide`, `OrderStatus`
- Response DTOs in `com.leapvelocity.dto.response`
- Request DTOs in `com.leapvelocity.dto.request`
- Jakarta Validation annotations on request DTOs
- Custom exceptions
- `OrderExecutionService`
- `PositionUpdateService`
- JUnit tests for entities, DTO mapping, enums, and services

The project currently passes:

```bash
mvn test -q
```

## Task 1: DTO Request And Response Separation
- Request DTOs represent client input.
- Response DTOs represent API output.
- Clients cannot send system-owned fields such as `id`, `status`, `createdOn`, or `version`.
- Sprint 6 controllers can use request DTOs with `@Valid`.

## Task 2: Clean Position Domain Methods

Status: should do before Sprint 6.

Current issue:

```java
public void apply(BigDecimal quantity, BigDecimal price)
```

`Position.apply(quantity, price)` currently sounds generic, but it is mainly used for buy logic because it recalculates average cost. It can also accept negative quantity, which makes the domain model confusing because sell logic already exists in `PositionUpdateService.applySell()`.

Recommended change:

Replace `apply(...)` with clearer methods:

```java
public void increaseForBuy(BigDecimal quantity, BigDecimal price)
```

Optional later:

```java
public void reduceForSell(BigDecimal quantity)
```

Then `PositionUpdateService` becomes clearer:

```java
position.increaseForBuy(order.getQuantity(), order.getPrice());
```

If sell reduction stays in `PositionUpdateService`, that is also acceptable for now. The important part is that the buy method should not allow negative quantity.

Why this matters:

- Better method names.
- Less confusing domain behavior.
- Avoids hidden support for short-selling or negative trades.
- Makes business rules easier to expose safely through REST APIs in Sprint 6.

Recommended priority: high.

## Task 3: Base Trading Exception

Status: good before Sprint 6.

Current exceptions:

- `AccountNotActiveException`
- `AccountNotFoundException`
- `DuplicateOrderException`
- `InstrumentNotFoundException`
- `InsufficientFundsException`
- `InsufficientHoldingsException`

These are good, but they currently extend `RuntimeException` directly.

Recommended change:

```java
package com.leapvelocity.exceptions;

public abstract class TradingException extends RuntimeException {
    protected TradingException(String message) {
        super(message);
    }
}
```

Then domain exceptions should extend `TradingException`:

```java
public class InsufficientFundsException extends TradingException {
    public InsufficientFundsException(Long accountId, BigDecimal requiredAmount, BigDecimal availableCash) {
        super("Insufficient funds for account " + accountId
                + ": required=" + requiredAmount
                + ", available=" + availableCash);
    }
}
```

Why this matters for Sprint 6:

Spring Boot global exception handling becomes simpler:

```java
@ExceptionHandler(TradingException.class)
public ResponseEntity<ErrorResponse> handleTradingException(TradingException ex) {
    ...
}
```

Recommended priority: medium-high.

## Task 4: Extract Order Validation

Status: useful before Sprint 6.

Current issue:

`OrderExecutionService` currently validates the `Order` object inside a private method.

Current responsibility inside `OrderExecutionService` includes:

- validates order fields
- checks duplicate idempotency keys
- checks account existence
- checks account status
- checks instrument tradability
- executes buy orders
- executes sell orders
- marks orders as `FILLED` or `REJECTED`
- stores orders in memory

This works, but the class has several responsibilities.

Recommended change:

Create:

```java
package com.leapvelocity.service;

public class OrderValidator {
    public void validate(Order order) {
        ...
    }
}
```

Then:

```java
orderValidator.validate(order);
```

Important distinction:

- Request DTO validation checks incoming API input.
- `OrderValidator` checks domain-level order validity before execution.

Why this matters:

- Makes `OrderExecutionService` more focused.
- Keeps controller validation separate from domain validation.
- Makes validation easier to test directly.

Recommended priority: medium.

## Task 5: Repository Interfaces

Status: optional before Sprint 6, important during Sprint 6.

Current issue:

`OrderExecutionService` and `PositionUpdateService` currently store data directly in memory using `HashMap`.

Current examples:

```java
private final Map<Long, Account> accountsById;
private final Map<String, Instrument> instrumentsBySymbol;
private final Map<String, Order> ordersByIdempotencyKey;
```

```java
private final Map<String, Position> positionsByAccountAndSymbol;
```

This is acceptable for Sprint 5 because the goal is a tested business engine. However, these collections should not stay inside services once Spring Boot and MyBatis are added.

Recommended interfaces:

```text
repository
  AccountRepository.java
  InstrumentRepository.java
  OrderRepository.java
  PositionRepository.java
```

Recommended temporary implementations:

```text
repository/inmemory
  InMemoryAccountRepository.java
  InMemoryInstrumentRepository.java
  InMemoryOrderRepository.java
  InMemoryPositionRepository.java
```

Suggested contracts:

```java
public interface AccountRepository {
    Optional<Account> findById(Long accountId);
    void save(Account account);
}
```

```java
public interface InstrumentRepository {
    Optional<Instrument> findBySymbol(String symbol);
    void save(Instrument instrument);
}
```

```java
public interface OrderRepository {
    boolean existsByIdempotencyKey(String idempotencyKey);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
    void save(Order order);
}
```

```java
public interface PositionRepository {
    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);
    List<Position> findByAccountId(Long accountId);
    void save(Position position);
    void delete(Position position);
}
```

Why this matters:

- Removes storage details from business services.
- Prepares for MyBatis persistence.
- Supports Dependency Inversion.
- Makes services easier to test.

Recommendation:

- If there is time before Sprint 6, add repository interfaces and in-memory implementations.
- If time is limited, leave this for Sprint 6 because persistence with MyBatis will naturally force this structure.

Recommended priority: medium before Sprint 6, high during Sprint 6.

## Task 6: Refactor OrderExecutionService Dependencies

Status: do after repository interfaces exist.

Current issue:

`OrderExecutionService` currently creates and owns its dependencies:

```java
this.accountsById = new HashMap<>();
this.instrumentsBySymbol = new HashMap<>();
this.ordersByIdempotencyKey = new HashMap<>();
this.positionUpdateService = new PositionUpdateService();
```

This means the service depends on concrete storage details.

Better direction:

```java
public OrderExecutionService(
        AccountRepository accountRepository,
        InstrumentRepository instrumentRepository,
        OrderRepository orderRepository,
        PositionUpdateService positionUpdateService,
        OrderValidator orderValidator
) {
    ...
}
```

Why this matters:

- Follows Dependency Inversion Principle.
- Makes Spring Boot dependency injection easier.
- Allows the service to use in-memory repositories in tests and MyBatis repositories in production.

Recommended priority: after Task 5.

## Task 7: Keep PositionUpdateService Focused

Status: keep and improve gradually.

`PositionUpdateService` is useful because it keeps position logic out of `OrderExecutionService`.

Current responsibilities:

- stores positions in memory
- creates positions for buy orders
- updates average cost for additional buys
- reduces positions for sell orders
- removes positions when fully sold
- rejects sells with insufficient holdings
- returns positions by account

Future improvement:

Once `PositionRepository` exists, `PositionUpdateService` should stop owning the `HashMap`. It should use `PositionRepository` instead.

Better responsibility:

```text
PositionUpdateService
  - applies buy/sell changes to positions
  - enforces position business rules
  - delegates storage to PositionRepository
```

Recommended priority: after repository interfaces.

## Recommended Work Order Before Sprint 6

1. Keep DTO request/response split as it is now.
2. Refactor `Position.apply(...)` into a clearer buy method such as `increaseForBuy(...)`.
3. Add `TradingException` and update custom exceptions to extend it.
4. Extract `OrderValidator` from `OrderExecutionService`.
5. Optionally add repository interfaces and in-memory repository implementations.
6. Refactor `OrderExecutionService` to receive dependencies through constructor injection.
7. Move in-memory collections out of services once repositories exist.

## Minimum Recommended Before Sprint 6

If time is limited, complete these before Sprint 6:

1. Clean `Position.apply(...)`.
2. Add base `TradingException`.
3. Extract `OrderValidator`.

Repositories can be done during Sprint 6 because MyBatis and Spring Boot layered architecture will naturally require them.

## What Not To Do Yet

Do not create interfaces for constants.

Use this only if constants are truly needed:

```java
public final class TradingConstants {
    public static final String DEFAULT_CURRENCY = "USD";

    private TradingConstants() {
    }
}
```

If a value is a business category, prefer an enum:

```java
public enum AssetClass {
    EQUITY,
    ETF,
    BOND
}
```

Do not create interfaces for entities such as `Account`, `Order`, `Position`, or `Instrument`. Interfaces are useful at boundaries, especially repositories and external integrations.

Do not make `Instrument` abstract unless different instrument types need different behavior. If the only difference is `assetClass`, use an enum instead.
