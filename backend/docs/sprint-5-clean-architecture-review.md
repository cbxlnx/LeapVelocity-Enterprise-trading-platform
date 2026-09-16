# Sprint 5 Clean Code, SOLID Review, And Sprint 6 Preparation

## Scope

This review covers only the `backend` folder of `LeapVelocity-Enterprise-trading-platform`.

The goal is to document what should be improved before Sprint 6, where the project will move into Spring Boot, REST API design, MyBatis persistence, and layered architecture.

## Current Status

The Sprint 5 backend skeleton already has:

- Domain entities: `Account`, `Instrument`, `Order`, `Position`
- Enums: `AccountStatus`, `OrderSide`, `OrderStatus`
- Request DTOs in `com.leapvelocity.dto.request`
- Response DTOs in `com.leapvelocity.dto.response`
- Jakarta Validation annotations on request DTOs
- Custom domain exceptions
- `OrderExecutionService`
- `PositionUpdateService`
- JUnit tests for entities, enums, DTO mapping, and services

Current tests pass with:

```bash
mvn test -q
```

## Task 1: Fix Immediate Clean-Code Defects

### 1.1 Fix `addInstrument` null handling

Current issue:

`OrderExecutionService.addInstrument(...)` trims the symbol only when it is not null, but then calls `symbol.isEmpty()`. If `instrument.getSymbol()` returns null, this can throw `NullPointerException`.

Recommended fix:

```java
symbol = instrument.getSymbol();
if (symbol == null || symbol.isBlank()) {
    throw new IllegalArgumentException("Instrument symbol is required");
}
symbol = symbol.trim();
```

Why this matters:

- Avoids a runtime null bug.
- Makes validation behavior predictable.
- Keeps service input validation clean.

Priority: high.

### 1.2 Remove typo method `isTradeble()`

Current issue:

`Instrument` has both:

```java
isTradeble()
isTradable()
```

Recommended fix:

- Keep `isTradable()`.
- Remove `isTradeble()` after confirming tests and code no longer use it.

Why this matters:

- Avoids duplicate API methods.
- Improves naming consistency.
- Prevents future code from using the misspelled method.

Priority: low-medium.

### 1.3 Move enum files to package-matching folder

Current issue:

Enum files are located under:

```text
src/main/java/com/leapvelocity/enums
```

but the package is:

```java
package com.leapvelocity.entities.enums;
```

Recommended structure:

```text
src/main/java/com/leapvelocity/entities/enums
```

Do the same for enum tests:

```text
src/test/java/com/leapvelocity/entities/enums
```

Why this matters:

- Java compiles it, but folder/package mismatch is confusing.
- IDE navigation and team readability are better when paths match packages.
- It avoids package structure surprises during Spring Boot migration.

Priority: medium.

## Task 2: DTO Request And Response

Status: done.

- Request DTOs represent client input.
- Response DTOs represent API output.
- Clients cannot send system-owned fields such as `id`, `status`, `createdOn`, or `version`.
- Sprint 6 controllers can use request DTOs with `@Valid`.

Current structure:

```text
dto
  request
    CreateAccountRequestDto.java
    CreateInstrumentRequestDto.java
    CreatePositionRequestDto.java
    PlaceOrderRequestDto.java

  response
    AccountDto.java
    InstrumentDto.java
    OrderDto.java
    PositionDto.java
```

Completed improvement:

- Added one focused validation test for `PlaceOrderRequestDto`.

Covered validation cases:

- blank `symbol`
- null `side`
- zero or negative `quantity`
- zero or negative `price`
- blank `idempotencyKey`

## Task 3: Clean `Position` Domain Methods

Status: should do before Sprint 6.

Current issue:

```java
public void apply(BigDecimal quantity, BigDecimal price)
```

`Position.apply(quantity, price)` sounds generic, but it is mainly buy logic because it recalculates average cost. It currently accepts negative quantity, and one test expects negative quantity to reduce a position. That conflicts with the existing design because sell logic already lives in `PositionUpdateService.applySell()`.

Recommended change:

Replace `apply(...)` with a clearer buy method:

```java
public void increaseForBuy(BigDecimal quantity, BigDecimal price)
```

This method should reject:

- null quantity
- null price
- zero or negative quantity
- zero or negative price

Then update `PositionUpdateService.applyBuy(...)`:

```java
position.increaseForBuy(order.getQuantity(), order.getPrice());
```

Optional later:

```java
public void reduceForSell(BigDecimal quantity)
```

If sell reduction stays in `PositionUpdateService`, that is acceptable for Sprint 5. The important part is to stop using one generic method for both buy and negative sell behavior.

Why this matters:

- Better method naming.
- Clearer domain rules.
- Avoids hidden support for short selling.
- Makes REST API behavior safer in Sprint 6.

Tests to update:

- Rename the existing positive buy test to use `increaseForBuy(...)`.
- Replace the negative quantity test with a rejection test.

Priority: high.

## Task 4: Add Base `TradingException`

Status: recommended before Sprint 6.

Current exceptions:

- `AccountNotActiveException`
- `AccountNotFoundException`
- `DuplicateOrderException`
- `InstrumentNotFoundException`
- `InsufficientFundsException`
- `InsufficientHoldingsException`

Current issue:

All custom exceptions extend `RuntimeException` directly.

Recommended change:

```java
package com.leapvelocity.exceptions;

public abstract class TradingException extends RuntimeException {
    protected TradingException(String message) {
        super(message);
    }
}
```

Then update domain exceptions:

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

Priority: medium-high.

## Task 5: Extract `OrderValidator`

Status: useful before Sprint 6.

Current issue:

`OrderExecutionService` currently validates orders inside a private method.

Current `OrderExecutionService` responsibilities include:

- stores accounts in memory
- stores instruments in memory
- stores orders by idempotency key
- validates order fields
- checks duplicate idempotency keys
- checks account existence
- checks account status
- checks instrument tradability
- executes buy orders
- executes sell orders
- marks orders as `FILLED` or `REJECTED`
- delegates position changes to `PositionUpdateService`

This works for a skeleton, but it is too many responsibilities for one service.

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

Then use:

```java
orderValidator.validate(order);
```

Important distinction:

- Request DTO validation checks incoming REST input.
- `OrderValidator` checks domain-level order validity before execution.

Why this matters:

- Supports Single Responsibility Principle.
- Makes order validation independently testable.
- Keeps future controller validation separate from business validation.

Priority: medium.

## Task 6: Introduce Repository Boundaries

Status: optional before Sprint 6, important during Sprint 6.

Current issue:

`OrderExecutionService` and `PositionUpdateService` store data directly in `HashMap`.

Current examples:

```java
private final Map<Long, Account> accountsById;
private final Map<String, Instrument> instrumentsBySymbol;
private final Map<String, Order> ordersByIdempotencyKey;
```

```java
private final Map<String, Position> positionsByAccountAndSymbol;
```

This is acceptable for Sprint 5 because the goal is a tested Java business engine. It should not remain this way once Spring Boot and MyBatis are introduced.

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
- Supports Dependency Inversion Principle.
- Makes services easier to test.

Recommendation:

- If time is limited, leave repository implementation for Sprint 6.
- If time is available, add interfaces and simple in-memory implementations before Sprint 6.

Priority: medium before Sprint 6, high during Sprint 6.

## Task 7: Refactor Service Dependencies

Status: do after repository interfaces exist.

Current issue:

`OrderExecutionService` creates and owns its dependencies:

```java
this.accountsById = new HashMap<>();
this.instrumentsBySymbol = new HashMap<>();
this.ordersByIdempotencyKey = new HashMap<>();
this.positionUpdateService = new PositionUpdateService();
```

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
- Allows in-memory repositories in tests and MyBatis repositories in production.

Priority: after Task 6.

## Task 8: Keep `PositionUpdateService` Focused

Status: keep and improve gradually.

Current responsibilities:

- stores positions in memory
- creates positions for buy orders
- updates average cost for additional buys
- reduces positions for sell orders
- removes positions when fully sold
- rejects sells with insufficient holdings
- returns positions by account

This is better than putting all position logic into `OrderExecutionService`.

Future direction:

After `PositionRepository` exists, `PositionUpdateService` should stop owning the `HashMap` and use the repository instead.

Better responsibility:

```text
PositionUpdateService
  - applies buy/sell changes to positions
  - enforces position business rules
  - delegates storage to PositionRepository
```

Priority: after repository interfaces.

## Recommended Order Before Sprint 6

1. Fix `addInstrument(...)` null handling.
2. Remove typo method `isTradeble()`.
3. Move enum files to a package-matching folder.
4. Keep DTO request/response split as it is now.
5. Refactor `Position.apply(...)` into `increaseForBuy(...)`.
6. Add `TradingException` and update custom exceptions.
7. Extract `OrderValidator`.
8. Optionally add repository interfaces and in-memory implementations.
9. Refactor services to receive dependencies through constructors.

## Minimum Recommended Before Sprint 6

If time is limited, complete only:

1. Fix `addInstrument(...)` null handling.
2. Refactor `Position.apply(...)`.
3. Add base `TradingException`.
4. Extract `OrderValidator`.

Repository interfaces can be done during Sprint 6 because MyBatis and Spring Boot layered architecture will naturally require them.

## What Not To Do Yet

Do not create interfaces for constants.

Use a constants class only when constants are truly needed:

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
