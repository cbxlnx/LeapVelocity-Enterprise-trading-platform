package com.leapvelocity.service;

import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.Position;
import com.leapvelocity.exceptions.InsufficientHoldingsException;
import com.leapvelocity.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PositionUpdateService {
	private final PositionRepository positionRepository;
	private final Map<String, Position> positionsByAccountAndSymbol;

	public PositionUpdateService() {
		this.positionRepository = null;
		this.positionsByAccountAndSymbol = new HashMap<>();
	}

	@Autowired
	public PositionUpdateService(PositionRepository positionRepository) {
		this.positionRepository = positionRepository;
		this.positionsByAccountAndSymbol = new HashMap<>();
	}

	public void addPosition(Position position) {
		String key;

		if (position == null) {
			throw new IllegalArgumentException("Position is required");
		}
		position.setSymbol(normalizeSymbol(position.getSymbol()));
		if (usesRepository()) {
			positionRepository.save(position);
			return;
		}
		key = positionKey(position.getAccountId(), position.getSymbol());
		positionsByAccountAndSymbol.put(key, position);
	}

	public Position getPosition(Long accountId, String symbol) {
		if (usesRepository()) {
			return positionRepository.findByAccountIdAndSymbol(accountId, normalizeSymbol(symbol)).orElse(null);
		}
		return positionsByAccountAndSymbol.get(positionKey(accountId, symbol));
	}

	public List<Position> getPositionsForAccount(Long accountId) {
		List<Position> positions;

		if (accountId == null) {
			throw new IllegalArgumentException("Account id is required");
		}
		if (usesRepository()) {
			return positionRepository.findByAccountId(accountId);
		}

		positions = new ArrayList<>();
		for (Position position : positionsByAccountAndSymbol.values()) {
			if (position.getAccountId() != null && position.getAccountId().equals(accountId)) {
				positions.add(position);
			}
		}

		return positions;
	}

	public void applyBuy(Order order) {
		Position position;
		String key;
		String symbol;

		symbol = normalizeSymbol(order.getSymbol());
		key = positionKey(order.getAccountId(), symbol);
		if (usesRepository()) {
			position = positionRepository.findByAccountIdAndSymbol(order.getAccountId(), symbol).orElse(null);

			if (position == null) {
				position = new Position(order.getAccountId(), symbol, order.getQuantity(), order.getPrice());
				positionRepository.save(position);
				return;
			}

			position.apply(order.getQuantity(), order.getPrice());
			positionRepository.save(position);
			return;
		}

		position = positionsByAccountAndSymbol.get(key);

		if (position == null) {
			position = new Position(order.getAccountId(), symbol, order.getQuantity(), order.getPrice());
			positionsByAccountAndSymbol.put(key, position);
			return;
		}

		position.apply(order.getQuantity(), order.getPrice());
	}

	public void applySell(Order order) {
		Position position;
		String key;
		BigDecimal remainingQuantity;
		String symbol;

		symbol = normalizeSymbol(order.getSymbol());
		key = positionKey(order.getAccountId(), symbol);
		if (usesRepository()) {
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
			return;
		}

		position = positionsByAccountAndSymbol.get(key);
		if (position == null) {
			throw new InsufficientHoldingsException(order.getAccountId(), symbol, order.getQuantity(), BigDecimal.ZERO);
		}

		if (position.getQuantity().compareTo(order.getQuantity()) < 0) {
			throw new InsufficientHoldingsException(order.getAccountId(), position.getSymbol(), order.getQuantity(), position.getQuantity());
		}

		remainingQuantity = position.getQuantity().subtract(order.getQuantity());
		if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
			positionsByAccountAndSymbol.remove(key);
		} else {
			position.setQuantity(remainingQuantity);
		}
	}

	private String positionKey(Long accountId, String symbol) {
		if (accountId == null) {
			throw new IllegalArgumentException("Account id is required");
		}
		return accountId + "|" + normalizeSymbol(symbol);
	}

	private String normalizeSymbol(String symbol) {
		if (symbol == null || symbol.isBlank()) {
			throw new IllegalArgumentException("Position symbol is required");
		}
		return symbol.trim();
	}

	private boolean usesRepository() {
		return positionRepository != null;
	}
}