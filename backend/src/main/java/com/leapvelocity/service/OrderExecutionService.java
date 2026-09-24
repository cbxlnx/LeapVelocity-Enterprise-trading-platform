package com.leapvelocity.service;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.exceptions.AccountNotActiveException;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.DuplicateOrderException;
import com.leapvelocity.exceptions.InsufficientFundsException;
import com.leapvelocity.exceptions.InstrumentNotFoundException;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderExecutionService {

	private final Map<Long, Account> accountsById;
	private final Map<String, Instrument> instrumentsBySymbol;
	private final Map<String, Order> ordersByIdempotencyKey;
	private final PositionUpdateService positionUpdateService;
	private final OrderValidator orderValidator;
	private final AccountRepository accountRepository;
	private final InstrumentRepository instrumentRepository;
	private final OrderRepository orderRepository;

	public OrderExecutionService() {
		this.accountsById = new HashMap<>();
		this.instrumentsBySymbol = new HashMap<>();
		this.ordersByIdempotencyKey = new HashMap<>();
		this.positionUpdateService = new PositionUpdateService();
		this.orderValidator = new OrderValidator();
		this.accountRepository = null;
		this.instrumentRepository = null;
		this.orderRepository = null;
	}

	@Autowired
	public OrderExecutionService(
			AccountRepository accountRepository,
			InstrumentRepository instrumentRepository,
			OrderRepository orderRepository,
			PositionUpdateService positionUpdateService) {
		this.accountsById = new HashMap<>();
		this.instrumentsBySymbol = new HashMap<>();
		this.ordersByIdempotencyKey = new HashMap<>();
		this.positionUpdateService = positionUpdateService;
		this.orderValidator = new OrderValidator();
		this.accountRepository = accountRepository;
		this.instrumentRepository = instrumentRepository;
		this.orderRepository = orderRepository;
	}

	public void addAccount(Account account) {
		if (account == null) {
			throw new IllegalArgumentException("Account is required");
		}
		if (usesRepository()) {
			accountRepository.save(account);
			return;
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
		if (symbol == null) {
			throw new IllegalArgumentException("Instrument symbol is required");
		}
		symbol = symbol.trim();
		if (symbol.isEmpty()) {
			throw new IllegalArgumentException("Instrument symbol is required");
		}

		instrument.setSymbol(symbol);
		if (usesRepository()) {
			instrumentRepository.save(instrument);
			return;
		}
		instrumentsBySymbol.put(symbol, instrument);
	}

	public void addPosition(Position position) {
		if (position == null) {
			throw new IllegalArgumentException("Position is required");
		}
		positionUpdateService.addPosition(position);
	}

	@Transactional
	public Order placeOrder(Order order) {
		BigDecimal notional;
		Account account;

		validateOrder(order);

		if (isDuplicateOrder(order.getIdempotencyKey())) {
			order.setStatus(OrderStatus.REJECTED);
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
		return saveOrder(order);
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
		if (usesRepository()) {
			return orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
		}
		return ordersByIdempotencyKey.get(idempotencyKey);
	}

	private void executeBuy(Order order, Account account, BigDecimal notional) {
		if (account.getCashBalance().compareTo(notional) < 0) {
			rejectOrder(order);
			throw new InsufficientFundsException(order.getAccountId(), notional, account.getCashBalance());
		}

		account.debit(notional);
		persistAccount(account);
		positionUpdateService.applyBuy(order);
	}

	private void executeSell(Order order, Account account, BigDecimal notional) {
		positionUpdateService.applySell(order);
		account.credit(notional);
		persistAccount(account);
	}

	private Account requireActiveAccount(Order order) {
		Account account;

		if (usesRepository()) {
			account = accountRepository.findById(order.getAccountId()).orElse(null);
		} else {
			account = accountsById.get(order.getAccountId());
		}
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

		if (usesRepository()) {
			instrument = instrumentRepository.findBySymbol(order.getSymbol()).orElse(null);
		} else {
			instrument = instrumentsBySymbol.get(order.getSymbol());
		}
		if (instrument == null || !instrument.isTradable()) {
			rejectOrder(order);
			throw new InstrumentNotFoundException(order.getSymbol());
		}
	}

	private void rejectOrder(Order order) {
		order.setStatus(OrderStatus.REJECTED);
		if (usesRepository()) {
			persistRejectedOrder(order);
			return;
		}
		ordersByIdempotencyKey.put(order.getIdempotencyKey(), order);
	}

	private void validateOrder(Order order) {
		orderValidator.validate(order);
	}

	private boolean usesRepository() {
		return accountRepository != null && instrumentRepository != null && orderRepository != null;
	}

	private boolean isDuplicateOrder(String idempotencyKey) {
		if (usesRepository()) {
			return orderRepository.existsByIdempotencyKey(idempotencyKey);
		}
		return ordersByIdempotencyKey.containsKey(idempotencyKey);
	}

	private void persistAccount(Account account) {
		if (usesRepository()) {
			accountRepository.save(account);
			return;
		}
		accountsById.put(account.getId(), account);
	}

	private Order saveOrder(Order order) {
		if (usesRepository()) {
			return orderRepository.save(order);
		}
		ordersByIdempotencyKey.put(order.getIdempotencyKey(), order);
		return order;
	}

	private void persistRejectedOrder(Order order) {
		if (!canPersistRejectedOrder(order)) {
			return;
		}
		orderRepository.save(order);
	}

	private boolean canPersistRejectedOrder(Order order) {
		if (order.getIdempotencyKey() == null || orderRepository.existsByIdempotencyKey(order.getIdempotencyKey())) {
			return false;
		}
		if (order.getAccountId() == null || !accountRepository.existsById(order.getAccountId())) {
			return false;
		}
		if (order.getSymbol() == null || order.getSymbol().isBlank()) {
			return false;
		}
		return instrumentRepository.findBySymbol(order.getSymbol().trim()).isPresent();
	}
}