package com.leapvelocity.service;

import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.exceptions.InsufficientHoldingsException;
import com.leapvelocity.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PositionUpdateService {
	private final PositionRepository positionRepository;

	@Autowired
	public PositionUpdateService(PositionRepository positionRepository) {
		this.positionRepository = positionRepository;
	}

	public void addPosition(Position position) {
		if (position == null) {
			throw new IllegalArgumentException("Position is required");
		}
		requireAccountId(position.getAccountId());
		position.setSymbol(normalizeSymbol(position.getSymbol()));
		positionRepository.save(position);
	}

	public Position getPosition(Long accountId, String symbol) {
		requireAccountId(accountId);
		return positionRepository.findByAccountIdAndSymbol(accountId, normalizeSymbol(symbol)).orElse(null);
	}

	public List<Position> getPositionsForAccount(Long accountId) {
		requireAccountId(accountId);
		return positionRepository.findByAccountId(accountId);
	}

	public void applyBuy(Order order) {
		Position position;
		String symbol;

		requireAccountId(order.getAccountId());
		symbol = normalizeSymbol(order.getSymbol());
		position = positionRepository.findByAccountIdAndSymbol(order.getAccountId(), symbol).orElse(null);

		if (position == null) {
			position = new Position(order.getAccountId(), symbol, order.getQuantity(), order.getPrice());
			positionRepository.save(position);
			return;
		}

		position.apply(order.getQuantity(), order.getPrice());
		positionRepository.save(position);
	}

	public void applySell(Order order) {
		Position position;
		BigDecimal remainingQuantity;
		String symbol;

		requireAccountId(order.getAccountId());
		symbol = normalizeSymbol(order.getSymbol());
		position = positionRepository.findByAccountIdAndSymbol(order.getAccountId(), symbol).orElse(null);
		if (position == null) {
			throw new InsufficientHoldingsException(order.getAccountId(), symbol, order.getQuantity(), BigDecimal.ZERO);
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

	public void reverseSell(Order order) {
		Position position;
		String symbol;

		requireAccountId(order.getAccountId());
		symbol = normalizeSymbol(order.getSymbol());
		position = positionRepository.findByAccountIdAndSymbol(order.getAccountId(), symbol).orElse(null);

		if (position == null) {
			position = new Position(order.getAccountId(), symbol, order.getQuantity(), order.getPrice());
			positionRepository.save(position);
			return;
		}

		position.setQuantity(position.getQuantity().add(order.getQuantity()));
		positionRepository.save(position);
	}

	private void requireAccountId(Long accountId) {
		if (accountId == null) {
			throw new IllegalArgumentException("Account id is required");
		}
	}

	private String normalizeSymbol(String symbol) {
		if (symbol == null || symbol.isBlank()) {
			throw new IllegalArgumentException("Position symbol is required");
		}
		return symbol.trim();
	}
}