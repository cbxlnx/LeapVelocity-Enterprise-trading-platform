package com.leapvelocity.service;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.OrderRepository;
import com.leapvelocity.repository.inmemory.InMemoryAccountRepository;
import com.leapvelocity.repository.inmemory.InMemoryInstrumentRepository;
import com.leapvelocity.repository.inmemory.InMemoryOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import com.leapvelocity.exceptions.AccountNotActiveException;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.DuplicateOrderException;
import com.leapvelocity.exceptions.InsufficientFundsException;
import com.leapvelocity.exceptions.InstrumentNotFoundException;


public class OrderExecutionService {

	private final AccountRepository accountRepository;
	private final InstrumentRepository instrumentRepository;
	private final OrderRepository orderRepository;
	private final PositionUpdateService positionUpdateService;
	private final OrderValidator orderValidator;

	public OrderExecutionService() {
		this(new InMemoryAccountRepository(),
			new InMemoryInstrumentRepository(),
			new InMemoryOrderRepository(),
			new PositionUpdateService(),
			new OrderValidator());
	}

	public OrderExecutionService(AccountRepository accountRepository,
			InstrumentRepository instrumentRepository,
			OrderRepository orderRepository,
			PositionUpdateService positionUpdateService,
			OrderValidator orderValidator) {
		this.accountRepository = accountRepository;
		this.instrumentRepository = instrumentRepository;
		this.orderRepository = orderRepository;
		this.positionUpdateService = positionUpdateService;
		this.orderValidator = orderValidator;
	}

	public void addAccount(Account account) {
		if (account == null) {
			throw new IllegalArgumentException("Account is required");
		}
		if (account.getId() == null) {
			throw new IllegalArgumentException("Account id is required");
		}
		accountRepository.save(account);
	}

	public void addInstrument(Instrument instrument) {
		String symbol;

		if (instrument == null) {
			throw new IllegalArgumentException("Instrument is required");
		}
		symbol = instrument.getSymbol();
		if (symbol == null || symbol.isBlank()) {
			throw new IllegalArgumentException("Instrument symbol is required");
		}
		symbol = symbol.trim();

		instrument.setSymbol(symbol);
		instrumentRepository.save(instrument);
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

		orderValidator.validate(order);

		if (orderRepository.existsByIdempotencyKey(order.getIdempotencyKey())) {
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
		orderRepository.save(order);
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
		return orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
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

		account = accountRepository.findById(order.getAccountId()).orElse(null);
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

		instrument = instrumentRepository.findBySymbol(order.getSymbol()).orElse(null);
		if (instrument == null || !instrument.isTradable()) {
			rejectOrder(order);
			throw new InstrumentNotFoundException(order.getSymbol());
		}
	}

	private void rejectOrder(Order order) {
		order.setStatus(OrderStatus.REJECTED);
		orderRepository.save(order);
	}

}
