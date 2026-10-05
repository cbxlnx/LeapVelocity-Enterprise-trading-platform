# Test Suite Documentation

This project contains unit tests for entities and services. All tests are **unit tests** that create objects directly in memory and verify behavior without starting a database or application context.

---

## Entity Tests

Located in: `com/leapvelocity/entities/`

These tests check the core domain entity classes (`Account`, `Instrument`, `Order`, `Position`) by verifying constructors, getters, setters, validation logic, and calculation methods.

### What Entity Tests Cover

- **`AccountTest`**
  - Constructor initializes defaults (ID, holder name, cash balance, status)
  - `debit()` subtracts cash and updates timestamp
  - `debit()` rejects insufficient funds with exception
  - `credit()` adds cash and updates timestamp
  - Both methods reject negative amounts
  - `isActive()` returns correct boolean based on `AccountStatus`

- **`InstrumentTest`**
  - Constructor stores all instrument details (symbol, name, asset class, currency)
  - Tradable flag can be toggled via `setTradable()`

- **`OrderTest`**
  - Constructor creates order with correct account ID, symbol, side, quantity, price
  - Order defaults to `OrderStatus.NEW`
  - `setStatus()` updates order status
  - `getCreatedOn()` returns non-null creation timestamp

- **`PositionTest`**
  - `apply()` increases quantity and recalculates average cost
  - `marketValue()` correctly multiplies quantity by current price
  - Both methods reject null values
  - Negative quantity handling for partial sales
  - Average cost formula: `(existingQty * existingCost + newQty * newPrice) / totalQty`

### What Entity Tests Do Not Cover

- Database persistence (no Hibernate or JPA)
- Entity relationships or cascade behavior
- Named queries or JPQL
- Transaction rollback scenarios

---

## Service Tests

Located in: `com/leapvelocity/service/`

These tests verify business logic for order execution and position management. They test service methods in isolation using in-memory accounts, instruments, and positions.

### `OrderExecutionServiceTest`

Tests the main order placement workflow with comprehensive coverage of buy/sell orders, account validation, and error handling.

**BUY ORDER TESTS:**
- Successful buy debits cash and creates/updates position
- Insufficient funds rejection with order marked as REJECTED
- Non-tradable instrument rejection
- Multiple buys increase position with correct average cost calculation

**SELL ORDER TESTS:**
- Successful sell credits cash and reduces position
- Insufficient holdings rejection
- Selling from non-existent position rejection
- Closing position when all shares sold

**ACCOUNT VALIDATION TESTS:**
- Suspended account rejection
- Closed account rejection
- Non-existent account rejection (all marked as REJECTED)

**IDEMPOTENCY TESTS:**
- Duplicate idempotency keys are rejected (prevents double-execution)

**INPUT VALIDATION TESTS:**
- Null order rejection
- Null account ID rejection
- Blank symbol rejection
- Zero/negative quantity rejection
- Zero/negative price rejection
- Blank idempotency key rejection

**INTEGRATION SCENARIOS:**
- Sequential buy orders with cash tracking
- Complex sequence: buy → partial sell → buy again

**HELPER VALIDATION TESTS:**
- Null account rejection when adding accounts
- Blank instrument symbol rejection when adding instruments

### `PositionUpdateServiceTest`

Tests position storage and update logic for both buy and sell orders, with extensive coverage of edge cases and isolation scenarios.

**POSITION STORAGE TESTS:**
- Add position and retrieve it with correct values
- Null position rejection

**BUY ORDER LOGIC TESTS:**
- New buy creates position with correct quantity and average cost
- Subsequent buy updates position and recalculates average cost
- Multiple sequential buys calculate correct cumulative average cost
  - Formula: `(qty1*price1 + qty2*price2 + ... + qtyn*priceN) / totalQty`

**SELL ORDER LOGIC TESTS:**
- Partial sell reduces position quantity
- Selling all shares removes position completely
- Sell from non-existent position throws exception
- Sell with insufficient holdings throws exception
- Transactional integrity: failed sell leaves position unchanged
- Exact holdings edge case: selling exactly the held quantity succeeds

**MULTI-ACCOUNT ISOLATION TESTS:**
- Positions for different accounts are independent
- Updating one account's position doesn't affect others
- `getPositionsForAccount()` returns correct account's positions
- Empty position list for non-existent account

**MULTI-SYMBOL ISOLATION TESTS:**
- Positions for different symbols in same account are independent
- Updating one symbol's position doesn't affect others

**INPUT VALIDATION TESTS:**
- Null account ID rejection
- Blank symbol rejection
- Null account ID in getPosition() rejection
- Blank symbol in getPosition() rejection

**EDGE CASES & SPECIAL SCENARIOS:**
- Fractional share quantities (e.g., 0.5 shares)
- Sell fractional shares correctly
- High-precision decimal prices maintain accuracy
- Very large quantities handled correctly (e.g., 1,000,000 shares)

---

## What These Tests Do Not Cover

- **Database persistence:** No Hibernate, JPA, or SQL verification
- **Spring context:** No application context startup
- **REST endpoints:** No HTTP request/response testing
- **Concurrency:** No multi-threaded scenarios
- **External systems:** No market data feeds or payment processors

---


## How To Run

From the project root directory:

```bash
mvn clean test
```

Run only entity tests:
```bash
mvn test -Dtest=AccountTest,InstrumentTest,OrderTest,PositionTest
```

Run only service tests:
```bash
mvn test -Dtest=OrderExecutionServiceTest,PositionUpdateServiceTest
```

Run a specific test class:
```bash
mvn test -Dtest=OrderExecutionServiceTest
```

Run a specific test method:
```bash
mvn test -Dtest=OrderExecutionServiceTest#buyOrderSuccess
```

**Note:** Use `clean` after moving or renaming packages so Maven removes stale compiled classes from `target/`.

---

## Test Types In This Project

- **Unit tests** (current): Test one class directly without external dependencies. Run fast (~1-2 seconds total).
![alt text](image.png)
