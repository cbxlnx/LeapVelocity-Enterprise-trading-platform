# Sprint 5 Backend Clean Architecture Review

### Domain Entities

The current domain entities are:

- `Account`
- `Instrument`
- `Order`
- `Position`

These match the Sprint 5 project documentation.

#### Account

`Account` represents a customer trading account.

It currently stores:

- internal database `id`
- external `accountId`
- holder name
- cash balance
- account status
- version
- last updated timestamp

It also contains useful domain behavior:

- `debit(amount)`
- `credit(amount)`
- `isActive()`

This is good domain design because balance changes are controlled through methods instead of only being changed externally through setters.

#### Instrument

`Instrument` represents a tradable security.

It currently stores:

- symbol
- name
- asset class
- currency
- tradable flag

It supports:

- checking whether an instrument is tradable.

Current improvement:

- Remove or rename the typo method `isTradeble()`. Keep `isTradable()` only.

#### Order

`Order` represents a customer buy or sell order.

It currently stores:

- account id
- symbol
- side
- quantity
- price
- status
- idempotency key
- created timestamp

This matches the Sprint 5 requirement for order placement logic.

#### Position

`Position` represents an account holding in one instrument.

It currently stores:

- account id
- symbol
- quantity
- average cost

It supports:

- applying a buy trade and recalculating average cost
- calculating market value

Current improvement:

- `Position.apply(quantity, price)` currently allows negative quantity. That can be confusing because sell logic is already handled in `PositionUpdateService.applySell()`. Prefer making `Position.apply()` clearly mean "apply buy" or split it into `increaseForBuy()` and `reduceForSell()`.

### Enums

The current enums are:

- `AccountStatus`: `ACTIVE`, `SUSPENDED`, `CLOSED`
- `OrderSide`: `BUY`, `SELL`
- `OrderStatus`: `NEW`, `FILLED`, `REJECTED`, `CANCELLED`

These match the Sprint 5 documentation.

Current improvement:

- The files are located under `src/main/java/com/leapvelocity/enums`, but their package is `com.leapvelocity.entities.enums`. Java can compile this, but it is confusing. Move files into a folder path that matches the package:

```text
src/main/java/com/leapvelocity/entities/enums
```

Do the same for the test files.

### DTOs

The current response DTO records are:

- `dto.response.AccountDto`
- `dto.response.InstrumentDto`
- `dto.response.OrderDto`
- `dto.response.PositionDto`

They currently support mapping from entity to DTO using static `from(...)` methods.

This is a good start because DTOs separate external response shape from the entity classes.

The current request DTO records are:

- `dto.request.CreateAccountRequestDto`
- `dto.request.CreateInstrumentRequestDto`
- `dto.request.CreatePositionRequestDto`
- `dto.request.PlaceOrderRequestDto`

These request DTOs use Jakarta Validation annotations such as `@NotNull`, `@NotBlank`, `@Positive`, and `@PositiveOrZero`.

This split prepares the project for Sprint 6 because REST controllers can validate input at the API boundary without exposing system-owned response fields such as `id`, `status`, `createdOn`, or `version` as client input.

### Exceptions

The current custom exceptions are:

- `AccountNotActiveException`
- `AccountNotFoundException`
- `DuplicateOrderException`
- `InstrumentNotFoundException`
- `InsufficientFundsException`
- `InsufficientHoldingsException`

These support the project requirement for a custom exception hierarchy.

Current improvement:

- Consider introducing a base exception later:

```java
public abstract class TradingException extends RuntimeException {
    protected TradingException(String message) {
        super(message);
    }
}
```

Then domain exceptions can extend `TradingException`.

This is not urgent for Sprint 5, but it will help Sprint 6 global exception handling.

### Services

The current services are:

- `OrderExecutionService`
- `PositionUpdateService`

#### OrderExecutionService

This service currently:

- stores accounts in memory
- stores instruments in memory
- stores orders by idempotency key in memory
- validates orders
- checks duplicate idempotency keys
- checks account existence
- checks account status
- checks instrument tradability
- executes buy orders
- executes sell orders
- marks orders as `FILLED` or `REJECTED`
- delegates position changes to `PositionUpdateService`

This gives the project a working business engine, but the class currently has several responsibilities.

#### PositionUpdateService

This service currently:

- stores positions in memory
- creates positions for buy orders
- updates average cost for additional buys
- reduces positions for sell orders
- removes positions when fully sold
- rejects sells with insufficient holdings
- returns positions by account

This is a useful separate service and supports single responsibility better than putting all position logic into `OrderExecutionService`.

## What It Does

The current backend can run core trading scenarios in memory.

Supported behavior:

- Add accounts.
- Add instruments.
- Add existing positions.
- Place buy orders.
- Place sell orders.
- Reject duplicate orders using idempotency key.
- Reject orders for missing accounts.
- Reject orders for inactive accounts.
- Reject orders for missing or non-tradable instruments.
- Reject buy orders with insufficient cash.
- Reject sell orders with insufficient holdings.
- Update cash balances after successful trades.
- Create, update, reduce, or remove positions.
- Map entities into DTO records.
- Validate core behavior through JUnit tests.

This is appropriate for Sprint 5 because Sprint 5 focuses on Java, OOAD, domain logic, SOLID, and unit tests, not yet full Spring Boot persistence.

## Clean Code And SOLID Review

### Single Responsibility Principle

Current state: partially followed.

Good:

- `Account` owns account balance behavior.
- `PositionUpdateService` owns most position changes.
- DTOs are separate from entities.
- Exceptions are separated by failure type.

Needs improvement:

- `OrderExecutionService` does too much. It currently validates, stores data, checks rules, executes orders, and manages order status.

Suggested next split:

```text
OrderExecutionService
  - orchestrates order placement

OrderValidator
  - validates required fields, positive price, positive quantity

AccountRepository
  - finds and saves accounts

InstrumentRepository
  - finds instruments by symbol

OrderRepository
  - checks idempotency and stores orders

PositionRepository
  - stores and retrieves positions

PositionUpdateService
  - applies buy/sell changes to positions
```

For Sprint 5, this split can be documented without implementing all of it immediately. For Sprint 6, repository interfaces become more important because Spring Boot and database persistence will be added.

### Open/Closed Principle

Current state: acceptable for the current scope.

The main place to watch is order execution:

```java
if (order.getSide() == OrderSide.BUY) {
    executeBuy(...);
} else if (order.getSide() == OrderSide.SELL) {
    executeSell(...);
}
```

This is fine while the only sides are `BUY` and `SELL`.

If more order types are added later, such as market order, limit order, stop order, or short sell, then consider strategy classes:

```text
OrderExecutionStrategy
  BuyOrderExecutionStrategy
  SellOrderExecutionStrategy
```

Not necessary now.

### Liskov Substitution Principle

Current state: no serious issue.

There is currently no inheritance hierarchy in the domain model.

The education example with an abstract `Instrument` is useful only when different instrument types need different behavior. For this project, `Instrument` is currently just data plus a tradable flag. It does not need to be abstract yet.

Use abstract classes or interfaces only if behavior differs, for example:

```java
public abstract class Instrument {
    public abstract BigDecimal calculateFee(BigDecimal tradeValue);
}
```

Then subclasses such as `EquityInstrument`, `BondInstrument`, or `EtfInstrument` would be useful.

If the only difference is the value of `assetClass`, use an enum instead of inheritance.

### Interface Segregation Principle

Current state: acceptable.

There are no large interfaces forcing classes to implement unused methods.

Do not create broad interfaces such as:

```java
public interface TradingService {
    void addAccount(...);
    void addInstrument(...);
    void addPosition(...);
    Order placeOrder(...);
    Position getPosition(...);
}
```

That would become too general.

If interfaces are added, keep them small and boundary-focused.

### Dependency Inversion Principle

Current state: needs improvement later.

`OrderExecutionService` currently depends directly on `HashMap` storage and directly creates `PositionUpdateService`.

Current:

```java
this.accountsById = new HashMap<>();
this.positionUpdateService = new PositionUpdateService();
```

Better later:

```java
public OrderExecutionService(
        AccountRepository accountRepository,
        InstrumentRepository instrumentRepository,
        OrderRepository orderRepository,
        PositionUpdateService positionUpdateService
) {
    ...
}
```

This makes the business service depend on abstractions instead of storage details.

For Sprint 5, in-memory collections are acceptable. For Sprint 6, repository interfaces or Spring repositories should be introduced.

## Should We Create Interfaces?

### Interfaces For Constants

No. Do not create interfaces only to hold constants.

Avoid:

```java
public interface TradingConstants {
    String DEFAULT_CURRENCY = "USD";
}
```

Better:

```java
public final class TradingConstants {
    public static final String DEFAULT_CURRENCY = "USD";

    private TradingConstants() {
    }
}
```

Even better, when the value is a business concept, use an enum:

```java
public enum AssetClass {
    EQUITY,
    ETF,
    BOND
}
```

For this project, possible future enums are:

- `AssetClass`
- `CurrencyCode`, only if currencies are limited by project scope

### Interfaces For Entities

No. Do not create interfaces for `Account`, `Order`, `Position`, or `Instrument` unless multiple interchangeable implementations are truly needed.

Entities are domain models. They should stay simple and expressive.

### Interfaces For Services

Not necessary yet for every service.

Useful later when:

- Spring Boot controllers depend on services.
- There are multiple implementations.
- You need to mock external dependencies.
- You need a stable boundary between application and infrastructure.

Example:

```java
public interface OrderPlacementUseCase {
    Order placeOrder(Order order);
}
```

This is useful if controllers should depend on a use-case contract instead of a concrete service.

### Interfaces For Repositories

Yes, this is the best place for interfaces when the project moves beyond a memory-only skeleton.

Recommended repository contracts:

```java
public interface AccountRepository {
    Optional<Account> findById(Long accountId);
    void save(Account account);
}
```

```java
public interface InstrumentRepository {
    Optional<Instrument> findBySymbol(String symbol);
}
```

```java
public interface OrderRepository {
    boolean existsByIdempotencyKey(String idempotencyKey);
    void save(Order order);
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
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

For Sprint 5, these could be implemented with in-memory maps. For Sprint 6, they can be implemented using MyBatis or Spring persistence.

## Should We Have Separate Collections?

Current in-memory maps are acceptable for the skeleton:

```java
Map<Long, Account> accountsById
Map<String, Instrument> instrumentsBySymbol
Map<String, Order> ordersByIdempotencyKey
Map<String, Position> positionsByAccountAndSymbol
```

However, the collections should not remain inside business services long term.

Recommended next structure:

```text
repository
  AccountRepository.java
  InstrumentRepository.java
  OrderRepository.java
  PositionRepository.java

repository/inmemory
  InMemoryAccountRepository.java
  InMemoryInstrumentRepository.java
  InMemoryOrderRepository.java
  InMemoryPositionRepository.java
```

This keeps storage separate from business rules.

## What Else Is Needed For Sprint 5

### Important

1. Keep request DTOs separate from response DTOs when adding Spring Boot controllers.
2. Fix `Instrument.isTradeble()` typo.
3. Move enum files so folder path matches package name.
4. Consider a base `TradingException` for easier global exception handling later.
5. Improve `OrderExecutionService.addInstrument()` null handling for symbol.
6. Decide whether `Position.apply()` should support negative quantity. Prefer explicit buy/sell methods.

### Good But Not Urgent

1. Add repository interfaces and in-memory implementations.
2. Inject dependencies into `OrderExecutionService` instead of constructing them inside the class.
3. Add an `AssetClass` enum if asset classes are fixed.
4. Add a small architecture diagram in `docs/`.
5. Add a short README section explaining how to run tests.

### Not Needed Yet

1. Interfaces for constants.
2. Interfaces for every entity.
3. Abstract `Instrument`, unless different instrument types have different behavior.
4. Full Spring Boot controller/service/repository layers. That belongs to Sprint 6.
5. Database repositories in Sprint 5, unless the team wants to prepare early for Sprint 6.

## Suggested Clean Architecture Direction

For Sprint 5, a simple clean structure is enough:

```text
com.leapvelocity
  entities
  entities.enums
  dto
  exceptions
  service
```

For Sprint 6, move toward:

```text
com.leapvelocity
  domain
    model
    enums
    exception
    repository
    service
  application
    dto
    usecase
  infrastructure
    persistence
  api
    controller
```

The important rule:

Business logic should not depend on controllers, databases, Kafka, or web frameworks.

The dependency direction should be:

```text
Controller -> Application Use Case -> Domain Service -> Repository Interface
Infrastructure Repository -> Repository Interface
```

## Sprint 5 Review Summary

The backend currently satisfies the main Sprint 5 skeleton goal:

- Domain entities exist.
- Enums exist.
- DTOs exist.
- Custom exceptions exist.
- Buy/sell business logic exists.
- Unit tests exist and pass.

The main improvements are architectural cleanup, not major rewrites:

- Keep entities simple.
- Avoid unnecessary interfaces.
- Do not use interfaces for constants.
- Introduce interfaces at boundaries, especially repositories.
- Move in-memory collections out of services when preparing for Sprint 6.
- Split validation from execution if `OrderExecutionService` keeps growing.

Recommended priority:

1. Fix package/folder consistency.
2. Keep DTO validation on request DTOs and avoid putting API validation concerns into entities.
3. Clean small naming and null-check issues.
4. Add repository boundaries when moving toward Spring Boot persistence.
