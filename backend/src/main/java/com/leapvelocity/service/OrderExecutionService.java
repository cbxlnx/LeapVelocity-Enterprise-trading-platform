package com.leapvelocity.service;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.leapvelocity.exceptions.AccountNotActiveException;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.DuplicateOrderException;
import com.leapvelocity.exceptions.InsufficientFundsException;
import com.leapvelocity.exceptions.InstrumentNotFoundException;


public class OrderExecutionService {

	private final Map<Long, Account> accountsById;
	private final Map<String, Instrument> instrumentsBySymbol;
	private final Map<String, Order> ordersByIdempotencyKey;
	private final PositionUpdateService positionUpdateService;

	public OrderExecutionService() {
		this.accountsById = new HashMap<>();
		this.instrumentsBySymbol = new HashMap<>();
		this.ordersByIdempotencyKey = new HashMap<>();
		this.positionUpdateService = new PositionUpdateService();
	}

	public void addAccount(Account account) {
		if (account == null) {
			throw new IllegalArgumentException("Account is required");
		}
		if (account.getId() == null) {
			throw new IllegalArgumentException("Account id is required");
		}
		accountsById.put(account.getId(), account);
	}

	public void addInstrument(Instrument instrument) {
		String symbol;

		if (instrument == null) {
			throw new IllegalArgumentException("Instrument is required");
		}
		symbol = instrument.getSymbol();
		if (symbol != null) {
			symbol = symbol.trim();
		}
		if (symbol.isEmpty()) {
			throw new IllegalArgumentException("Instrument symbol is required");
		}

		instrument.setSymbol(symbol);
		instrumentsBySymbol.put(symbol, instrument);
	}

	public void addPosition(Position position) {
		if (position == null) {
			throw new IllegalArgumentException("Position is required");
		}
		positionUpdateService.addPosition(position);
	}

	public Order placeOrder(Order order) {
		BigDecimal notional;
		Account account;

		validateOrder(order);

		if (ordersByIdempotencyKey.containsKey(order.getIdempotencyKey())) {
			rejectOrder(order);
			throw new DuplicateOrderException(order.getIdempotencyKey());
		}

		account = requireActiveAccount(order);
		requireTradableInstrument(order);

		notional = order.getQuantity().multiply(order.getPrice());
		if (order.getSide() == OrderSide.BUY) {
			executeBuy(order, account, notional);
		} else if (order.getSide() == OrderSide.SELL) {
			executeSell(order, account, notional);
		} else {
			throw new IllegalArgumentException("Unsupported order side: " + order.getSide());
		}

		order.setStatus(OrderStatus.FILLED);
		ordersByIdempotencyKey.put(order.getIdempotencyKey(), order);
		return order;
	}

	public Order execute(Order order) {
		return placeOrder(order);
	}

	public Position getPosition(Long accountId, String symbol) {
		return positionUpdateService.getPosition(accountId, symbol);
	}

	public List<Position> getPositionsForAccount(Long accountId) {
		return positionUpdateService.getPositionsForAccount(accountId);
	}

	public Order getOrder(String idempotencyKey) {
		return ordersByIdempotencyKey.get(idempotencyKey);
	}

	private void executeBuy(Order order, Account account, BigDecimal notional) {
		if (account.getCashBalance().compareTo(notional) < 0) {
			rejectOrder(order);
			throw new InsufficientFundsException(order.getAccountId(), notional, account.getCashBalance());
		}

		account.debit(notional);
		positionUpdateService.applyBuy(order);
	}

	private void executeSell(Order order, Account account, BigDecimal notional) {
		positionUpdateService.applySell(order);
		account.credit(notional);
	}

	private Account requireActiveAccount(Order order) {
		Account account;

		account = accountsById.get(order.getAccountId());
		if (account == null) {
			rejectOrder(order);
			throw new AccountNotFoundException(order.getAccountId());
		}

		if (!account.isActive()) {
			rejectOrder(order);
			throw new AccountNotActiveException(order.getAccountId());
		}

		return account;
	}

	private void requireTradableInstrument(Order order) {
		Instrument instrument;

		instrument = instrumentsBySymbol.get(order.getSymbol());
		if (instrument == null || !instrument.isTradable()) {
			rejectOrder(order);
			throw new InstrumentNotFoundException(order.getSymbol());
		}
	}

	private void rejectOrder(Order order) {
		order.setStatus(OrderStatus.REJECTED);
		ordersByIdempotencyKey.put(order.getIdempotencyKey(), order);
	}

	private void validateOrder(Order order) {
		if (order == null) {
			throw new IllegalArgumentException("Order is required");
		}
		if (order.getAccountId() == null) {
			throw new IllegalArgumentException("Order account id is required");
		}
		if (order.getSide() == null) {
			throw new IllegalArgumentException("Order side is required");
		}
		if (order.getSymbol() == null || order.getSymbol().isBlank()) {
			throw new IllegalArgumentException("Order symbol is required");
		}
		if (order.getIdempotencyKey() == null || order.getIdempotencyKey().isBlank()) {
			throw new IllegalArgumentException("Order idempotency key is required");
		}
		if (order.getQuantity() == null || order.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Order quantity must be greater than zero");
		}
		if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Order price must be greater than zero");
		}
		order.setSymbol(order.getSymbol().trim());
	}
}