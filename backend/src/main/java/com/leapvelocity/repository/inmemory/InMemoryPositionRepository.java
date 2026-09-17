package com.leapvelocity.repository.inmemory;

import com.leapvelocity.entities.Position;
import com.leapvelocity.repository.PositionRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryPositionRepository implements PositionRepository {

	private final Map<String, Position> positionsByAccountAndSymbol;

	public InMemoryPositionRepository() {
		this.positionsByAccountAndSymbol = new HashMap<>();
	}

	@Override
	public Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol) {
		return Optional.ofNullable(positionsByAccountAndSymbol.get(positionKey(accountId, symbol)));
	}

	@Override
	public List<Position> findByAccountId(Long accountId) {
		List<Position> positions;

		positions = new ArrayList<>();
		for (Position position : positionsByAccountAndSymbol.values()) {
			if (position.getAccountId() != null && position.getAccountId().equals(accountId)) {
				positions.add(position);
			}
		}

		return positions;
	}

	@Override
	public void save(Position position) {
		positionsByAccountAndSymbol.put(positionKey(position.getAccountId(), position.getSymbol()), position);
	}

	@Override
	public void delete(Position position) {
		positionsByAccountAndSymbol.remove(positionKey(position.getAccountId(), position.getSymbol()));
	}

	private String positionKey(Long accountId, String symbol) {
		if (accountId == null) {
			throw new IllegalArgumentException("Account id is required");
		}
		if (symbol == null || symbol.isBlank()) {
			throw new IllegalArgumentException("Position symbol is required");
		}
		return accountId + "|" + symbol.trim();
	}
}
