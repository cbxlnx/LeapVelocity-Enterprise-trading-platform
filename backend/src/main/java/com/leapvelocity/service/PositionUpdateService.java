package com.leapvelocity.service;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.repository.PositionRepository;
import com.leapvelocity.repository.inmemory.InMemoryPositionRepository;
import java.math.BigDecimal;
import java.util.List;
import com.leapvelocity.exceptions.InsufficientHoldingsException;

public class PositionUpdateService {
	private final PositionRepository positionRepository;

	public PositionUpdateService() {
		this(new InMemoryPositionRepository());
	}

	public PositionUpdateService(PositionRepository positionRepository) {
		this.positionRepository = positionRepository;
	}

	public void addPosition(Position position) {
		if (position == null) {
			throw new IllegalArgumentException("Position is required");
		}
		positionRepository.save(position);
	}

	public Position getPosition(Long accountId, String symbol) {
		return positionRepository.findByAccountIdAndSymbol(accountId, symbol).orElse(null);
	}

	public List<Position> getPositionsForAccount(Long accountId) {
		return positionRepository.findByAccountId(accountId);
	}

	public void applyBuy(Order order) {
		Position position;

		validateBuy(order.getQuantity(), order.getPrice());

		position = getPosition(order.getAccountId(), order.getSymbol());

		if (position == null) {
			position = new Position(order.getAccountId(), order.getSymbol(), order.getQuantity(), order.getPrice());
			positionRepository.save(position);
			return;
		}

		position.increaseForBuy(order.getQuantity(), order.getPrice());
		positionRepository.save(position);
	}

	public void applySell(Order order) {
		Position position;
		BigDecimal remainingQuantity;

		position = getPosition(order.getAccountId(), order.getSymbol());
		if (position == null) {
			throw new InsufficientHoldingsException(order.getAccountId(), order.getSymbol(), order.getQuantity(), BigDecimal.ZERO);
		}

		if (position.getQuantity().compareTo(order.getQuantity()) < 0) {
			throw new InsufficientHoldingsException(order.getAccountId(), position.getSymbol(), order.getQuantity(), position.getQuantity());
		}

		remainingQuantity = position.getQuantity().subtract(order.getQuantity());
		if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
			positionRepository.delete(position);
		} else {
			position.setQuantity(remainingQuantity);
			positionRepository.save(position);
		}
	}

	private void validateBuy(BigDecimal quantity, BigDecimal price) {
		if (quantity == null || price == null) {
			throw new IllegalArgumentException("Quantity and price cannot be null");
		}
		if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Quantity must be positive");
		}
		if (price.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Price must be positive");
		}
	}
}
