package com.leapvelocity.controller;

import com.leapvelocity.dto.response.HistoricalPricePointDto;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.repository.HistoricalPriceRepository;
import com.leapvelocity.repository.InstrumentRepository;
import com.leapvelocity.service.HistoricalPriceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HistoricalPriceControllerTest {
    private InstrumentRepository instruments;
    private HistoricalPriceRepository prices;
    private MockMvc mvc;
    private final LocalDate from = LocalDate.of(2025, 10, 1);
    private final LocalDate to = LocalDate.of(2025, 10, 2);

    @BeforeEach
    void setUp() {
        instruments = mock(InstrumentRepository.class);
        prices = mock(HistoricalPriceRepository.class);
        when(instruments.findBySymbol("AAPL")).thenReturn(Optional.of(
                new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true)));
        mvc = MockMvcBuilders.standaloneSetup(new HistoricalPriceController(
                        new HistoricalPriceService(instruments, prices)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsSeparateCloseAndAdjustedPricesWithCurrencyAndIsoDates() throws Exception {
        when(prices.findPrices("AAPL", from, to)).thenReturn(List.of(
                new HistoricalPricePointDto(from, new BigDecimal("250.123456"),
                        new BigDecimal("248.654321"), 123456789L, "yfinance")));
        mvc.perform(get("/api/v1/market-data/prices/aapl").param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.from").value("2025-10-01"))
                .andExpect(jsonPath("$.to").value("2025-10-02"))
                .andExpect(jsonPath("$.prices[0].date").value("2025-10-01"))
                .andExpect(jsonPath("$.prices[0].close").value(250.123456))
                .andExpect(jsonPath("$.prices[0].adjustedClose").value(248.654321))
                .andExpect(jsonPath("$.prices[0].source").value("yfinance"));
    }

    @Test
    void knownInstrumentWithoutHistoryReturnsEmptyList() throws Exception {
        when(prices.findPrices("AAPL", from, to)).thenReturn(List.of());
        mvc.perform(get("/api/v1/market-data/prices/AAPL").param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prices").isEmpty());
    }

    @Test
    void unknownInstrumentReturns404() throws Exception {
        mvc.perform(get("/api/v1/market-data/prices/UNKNOWN").param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INS-404"));
        verifyNoInteractions(prices);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "?from=2025-10-03&to=2025-10-01",
            "?from=invalid&to=2025-10-01",
            "?from=2025-02-30&to=2025-10-01",
            "?from=2025-10-01",
            "?to=2025-10-01",
            ""
    })
    void invalidOrMissingDatesReturn422WithoutReadingPrices(String query) throws Exception {
        mvc.perform(get("/api/v1/market-data/prices/AAPL" + query))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VAL-422"));
        verifyNoInteractions(prices);
    }
}
