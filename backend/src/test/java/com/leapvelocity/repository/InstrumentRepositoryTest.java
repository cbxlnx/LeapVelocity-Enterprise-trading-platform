package com.leapvelocity.repository;

import com.leapvelocity.entities.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("InstrumentRepository - prefix search")
class InstrumentRepositoryTest {

    @Autowired
    private InstrumentRepository instrumentRepository;

    @BeforeEach
    void seedInstruments() {
        instrumentRepository.saveAll(List.of(
                new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true),
                new Instrument("AMZN", "Amazon.com Inc.", "EQUITY", "USD", true),
                new Instrument("SPY", "SPDR S&P 500 ETF", "FUND", "USD", true),
                new Instrument("CASHUSD", "Cash balance", "CASH", "USD", true)
        ));
    }

    @Test
    @DisplayName("matches symbol prefix ignoring case")
    void matchesSymbolPrefixIgnoringCase() {
        assertSymbols("aa", 10, "AAPL");
    }

    @Test
    @DisplayName("matches name prefix ignoring case")
    void matchesNamePrefixIgnoringCase() {
        assertSymbols("spdr", 10, "SPY");
    }

    @Test
    @DisplayName("excludes CASH assets")
    void excludesCashAssets() {
        assertSymbols("cash", 10);
    }

    @Test
    @DisplayName("orders by symbol and honours the page size")
    void ordersBySymbolAndHonoursPageSize() {
        assertSymbols("a", 10, "AAPL", "AMZN");
        assertSymbols("a", 1, "AAPL");
    }

    private void assertSymbols(String prefix, int pageSize, String... expectedSymbols) {
        List<String> symbols = instrumentRepository.searchByPrefix(prefix, PageRequest.of(0, pageSize))
                .stream()
                .map(Instrument::getSymbol)
                .toList();
        assertEquals(List.of(expectedSymbols), symbols);
    }
}
