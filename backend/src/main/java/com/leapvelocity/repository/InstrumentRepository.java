package com.leapvelocity.repository;

import com.leapvelocity.entities.Instrument;

import java.util.Optional;

public interface InstrumentRepository {

	Optional<Instrument> findBySymbol(String symbol);

	void save(Instrument instrument);
}
