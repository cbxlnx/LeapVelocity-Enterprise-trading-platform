package com.leapvelocity.repository;

import com.leapvelocity.entities.Instrument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    Optional<Instrument> findBySymbol(String symbol);

    /**
     * Instruments whose symbol or name starts with the prefix, ignoring case. CASH pseudo-symbols are excluded.
     */
    @Query("""
            select i from Instrument i
            where i.assetClass <> 'CASH'
              and (lower(i.symbol) like lower(concat(:prefix, '%'))
                   or lower(i.name) like lower(concat(:prefix, '%')))
            order by i.symbol
            """)
    List<Instrument> searchByPrefix(@Param("prefix") String prefix, Pageable pageable);
}