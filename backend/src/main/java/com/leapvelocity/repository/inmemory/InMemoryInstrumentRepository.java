package com.leapvelocity.repository.inmemory;

import com.leapvelocity.entities.Instrument;
import com.leapvelocity.repository.InstrumentRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryInstrumentRepository implements InstrumentRepository {

	private final Map<String, Instrument> instrumentsBySymbol;

	public InMemoryInstrumentRepository() {
		this.instrumentsBySymbol = new HashMap<>();
	}

	@Override
	public Optional<Instrument> findBySymbol(String symbol) {
		if (symbol == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(instrumentsBySymbol.get(symbol.trim()));
	}

	@Override
	public void save(Instrument instrument) {
		instrumentsBySymbol.put(instrument.getSymbol(), instrument);
	}
}
