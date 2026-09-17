package com.leapvelocity.repository;

import com.leapvelocity.entities.Position;

import java.util.List;
import java.util.Optional;

public interface PositionRepository {

	Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);

	List<Position> findByAccountId(Long accountId);

	void save(Position position);

	void delete(Position position);
}
