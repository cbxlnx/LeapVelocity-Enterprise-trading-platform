package com.leapvelocity.repository;

import com.leapvelocity.entities.Position;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol);

    List<Position> findByAccountId(Long accountId);

    List<Position> findByAccountIdOrderBySymbolAsc(Long accountId);
}
