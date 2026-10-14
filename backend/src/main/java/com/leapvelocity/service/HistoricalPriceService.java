package com.leapvelocity.service;

import com.leapvelocity.dto.response.HistoricalPricesDto;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.exceptions.InstrumentNotFoundException;
import com.leapvelocity.repository.HistoricalPriceRepository;
import com.leapvelocity.repository.InstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;

@Service
public class HistoricalPriceService {
    private final InstrumentRepository instrumentRepository;
    private final HistoricalPriceRepository historicalPriceRepository;

    public HistoricalPriceService(InstrumentRepository instrumentRepository,
                                  HistoricalPriceRepository historicalPriceRepository) {
        this.instrumentRepository = instrumentRepository;
        this.historicalPriceRepository = historicalPriceRepository;
    }

    @Transactional(readOnly = true)
    public HistoricalPricesDto getPrices(String symbol, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both from and to dates are required (YYYY-MM-DD)");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must be on or before to");
        }
        if (symbol == null || symbol.isBlank() || symbol.trim().length() > 20) {
            throw new IllegalArgumentException("symbol must contain between 1 and 20 characters");
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        Instrument instrument = instrumentRepository.findBySymbol(normalized)
                .orElseThrow(() -> new InstrumentNotFoundException(normalized));
        return new HistoricalPricesDto(normalized, instrument.getCurrency(), from, to,
                historicalPriceRepository.findPrices(normalized, from, to));
    }
}
