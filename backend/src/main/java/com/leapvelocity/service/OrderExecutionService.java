package com.leapvelocity.service;
import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.Execution;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.exceptions.AccountNotActiveException;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.DuplicateOrderException;
import com.leapvelocity.exceptions.InsufficientFundsException;
import com.leapvelocity.exceptions.InsufficientHoldingsException;
import com.leapvelocity.exceptions.InstrumentNotFoundException;
import com.leapvelocity.exceptions.OrderNotFoundException;
import com.leapvelocity.messaging.ExecutionEvent;
import com.leapvelocity.messaging.OrderEventPublisher;
import com.leapvelocity.repository.AccountRepository;
import com.leapvelocity.repository.ExecutionRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrderExecutionService {

	private final PositionUpdateService positionUpdateService;
	private final OrderValidator orderValidator;
	private final AccountRepository accountRepository;
	private final InstrumentRepository instrumentRepository;
	private final OrderRepository orderRepository;
	private final ExecutionRepository executionRepository;
	private final OrderEventPublisher orderEventPublisher;

	@Autowired
	public OrderExecutionService(
			AccountRepository accountRepository,
			InstrumentRepository instrumentRepository,
			OrderRepository orderRepository,
			ExecutionRepository executionRepository,
			PositionUpdateService positionUpdateService,
			OrderEventPublisher orderEventPublisher) {
		this.positionUpdateService = positionUpdateService;
		this.orderValidator = new OrderValidator();
		this.accountRepository = accountRepository;
		this.instrumentRepository = instrumentRepository;
		this.orderRepository = orderRepository;
		this.executionRepository = executionRepository;
		this.orderEventPublisher = orderEventPublisher;
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
		if (symbol == null) {
			throw new IllegalArgumentException("Instrument symbol is required");
		}
		symbol = symbol.trim();
		if (symbol.isEmpty()) {
			throw new IllegalArgumentException("Instrument symbol is required");
		}

		instrument.setSymbol(symbol);
		instrumentRepository.save(instrument);
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
			ensureAcceptedBuy(order, account, notional);
		} else if (order.getSide() == OrderSide.SELL) {
			ensureAcceptedSell(order);
		} else {
			throw new IllegalArgumentException("Unsupported order side: " + order.getSide());
		}

		order.setStatus(OrderStatus.NEW);
		Order savedOrder = saveOrder(order);
		publishOrderAfterCommit(savedOrder);
		return savedOrder;
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

	@Transactional
	public void settleExecution(ExecutionEvent fill) {
		Account account;
		Instrument instrument;
		BigDecimal notional;
		Order order;

		if (fill == null) {
			throw new IllegalArgumentException("Execution event is required");
		}

		order = orderRepository.findById(fill.orderId()).orElse(null);
		if (order == null || order.getStatus() != OrderStatus.NEW) {
			return;
		}

		if (fill.quantity().compareTo(order.getQuantity()) != 0) {
			rejectSettledOrder(order);
			return;
		}

		account = accountRepository.findById(order.getAccountId()).orElse(null);
		if (account == null || !account.isActive()) {
			rejectSettledOrder(order);
			return;
		}

		instrument = instrumentRepository.findBySymbol(order.getSymbol()).orElse(null);
		if (instrument == null || !instrument.isTradable()) {
			rejectSettledOrder(order);
			return;
		}

		notional = fill.quantity().multiply(fill.price());
		try {
			if (order.getSide() == OrderSide.BUY) {
				settleBuy(order, account, fill, notional);
			} else if (order.getSide() == OrderSide.SELL) {
				settleSell(order, account, fill, notional);
			} else {
				rejectSettledOrder(order);
				return;
			}
		} catch (InsufficientFundsException | InsufficientHoldingsException ex) {
			rejectSettledOrder(order);
			return;
		}

		persistExecution(order, fill);
		order.setStatus(OrderStatus.FILLED);
		saveOrder(order);
	}

	public Order getOrder(String idempotencyKey) {
		return orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
	}

	@Transactional
	public Order cancelOrder(UUID orderId) {
		Order order;

		order = orderRepository.findById(orderId)
				.orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

		// Cannot cancel if already filled, rejected, or already cancelled
		if (order.getStatus() != OrderStatus.NEW) {
			throw new IllegalArgumentException(
					"Cannot cancel order with status: " + order.getStatus());
		}

		// Mark order as cancelled
		order.setStatus(OrderStatus.CANCELLED);
		return saveOrder(order);
	}

	private void ensureAcceptedBuy(Order order, Account account, BigDecimal notional) {
		if (account.getCashBalance().compareTo(notional) < 0) {
			rejectOrder(order);
			throw new InsufficientFundsException(order.getAccountId(), notional, account.getCashBalance());
		}
	}

	private void ensureAcceptedSell(Order order) {
		Position position;

		position = positionUpdateService.getPosition(order.getAccountId(), order.getSymbol());
		if (position == null) {
			rejectOrder(order);
			throw new InsufficientHoldingsException(order.getAccountId(), order.getSymbol(), order.getQuantity(), BigDecimal.ZERO);
		}

		if (position.getQuantity().compareTo(order.getQuantity()) < 0) {
			rejectOrder(order);
			throw new InsufficientHoldingsException(order.getAccountId(), position.getSymbol(), order.getQuantity(), position.getQuantity());
		}
	}

	private void settleBuy(Order order, Account account, ExecutionEvent fill, BigDecimal notional) {
		Order settlementOrder;

		if (account.getCashBalance().compareTo(notional) < 0) {
			throw new InsufficientFundsException(order.getAccountId(), notional, account.getCashBalance());
		}

		settlementOrder = settlementOrder(order, fill);
		account.debit(notional);
		persistAccount(account);
		positionUpdateService.applyBuy(settlementOrder);
	}

	private void settleSell(Order order, Account account, ExecutionEvent fill, BigDecimal notional) {
		Order settlementOrder;

		settlementOrder = settlementOrder(order, fill);
		positionUpdateService.applySell(settlementOrder);
		account.credit(notional);
		persistAccount(account);
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
		persistRejectedOrder(order);
	}

	private void rejectSettledOrder(Order order) {
		order.setStatus(OrderStatus.REJECTED);
		saveOrder(order);
	}

	private void validateOrder(Order order) {
		orderValidator.validate(order);
	}

	private boolean isDuplicateOrder(String idempotencyKey) {
		return orderRepository.existsByIdempotencyKey(idempotencyKey);
	}

	private void persistAccount(Account account) {
		accountRepository.save(account);
	}

	private Order saveOrder(Order order) {
		return orderRepository.save(order);
	}

	private void persistExecution(Order order, ExecutionEvent fill) {
		Execution execution;

		execution = new Execution(
				order.getId(),
				order.getAccountId(),
				order.getSymbol(),
				order.getSide(),
				fill.quantity(),
				fill.price());
		execution.setId(fill.executionId());
		execution.setExecutedOn(LocalDateTime.ofInstant(fill.executedOn(), ZoneOffset.UTC));
		executionRepository.save(execution);
	}

	private void publishOrderAfterCommit(Order order) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			orderEventPublisher.publish(order);
			return;
		}

		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				orderEventPublisher.publish(order);
			}
		});
	}

	private Order settlementOrder(Order order, ExecutionEvent fill) {
		Order settlementOrder;

		settlementOrder = new Order();
		settlementOrder.setId(order.getId());
		settlementOrder.setAccountId(order.getAccountId());
		settlementOrder.setSymbol(order.getSymbol());
		settlementOrder.setSide(order.getSide());
		settlementOrder.setQuantity(order.getQuantity());
		settlementOrder.setPrice(fill.price());
		settlementOrder.setStatus(order.getStatus());
		settlementOrder.setIdempotencyKey(order.getIdempotencyKey());
		settlementOrder.setCreatedOn(order.getCreatedOn());
		return settlementOrder;
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