package com.leapvelocity.controller;

import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.messaging.MarketDataEvent;
import com.leapvelocity.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("MarketDataController")
class MarketDataControllerTest {

    private MarketDataService marketDataService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        marketDataService = new MarketDataService();
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketDataController(marketDataService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/market-data/quotes/{symbol} - returns the latest quote")
    void getQuoteReturnsLatestQuote() throws Exception {
        marketDataService.update(new MarketDataEvent("AAPL", new BigDecimal("187.25"), new BigDecimal("187.20"),
                new BigDecimal("187.30"), "USD", Instant.parse("2026-05-01T12:00:00Z"), "yfinance"));

        mockMvc.perform(get("/api/v1/market-data/quotes/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.lastPrice").value(187.25))
                .andExpect(jsonPath("$.asOf").exists())
                .andExpect(jsonPath("$.source").value("yfinance"));
    }

    @Test
    @DisplayName("GET /api/v1/market-data/quotes/{symbol} - returns 404 when no quote has arrived")
    void getQuoteReturnsNotFoundWhenNoQuoteArrived() throws Exception {
        mockMvc.perform(get("/api/v1/market-data/quotes/MSFT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MKT-404"))
                .andExpect(jsonPath("$.message").value("Quote not found"))
                .andExpect(jsonPath("$.details").value("No quote available for symbol: MSFT"));
    }

    @Test
    @DisplayName("GET /api/v1/market-data/quotes - returns every requested quote")
    void getQuotesReturnsAllRequestedQuotes() throws Exception {
        update("AAPL", "187.25");
        update("MSFT", "380.50");

        mockMvc.perform(get("/api/v1/market-data/quotes").param("symbols", "AAPL,MSFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes.length()").value(2))
                .andExpect(jsonPath("$.quotes[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.quotes[1].lastPrice").value(380.50))
                .andExpect(jsonPath("$.missingSymbols").isEmpty())
                .andExpect(jsonPath("$.serverTimestamp").exists());
    }

    @Test
    @DisplayName("GET /api/v1/market-data/quotes - lists unknown symbols as missing")
    void getQuotesReportsPartialAvailability() throws Exception {
        update("AAPL", "187.25");

        mockMvc.perform(get("/api/v1/market-data/quotes").param("symbols", "AAPL,MSFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes.length()").value(1))
                .andExpect(jsonPath("$.quotes[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.missingSymbols.length()").value(1))
                .andExpect(jsonPath("$.missingSymbols[0]").value("MSFT"));
    }

    @Test
    @DisplayName("GET /api/v1/market-data/quotes - returns 200 with no quotes when none have arrived")
    void getQuotesReturnsOkWhenNoneFound() throws Exception {
        mockMvc.perform(get("/api/v1/market-data/quotes").param("symbols", "MSFT,NVDA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes").isEmpty())
                .andExpect(jsonPath("$.missingSymbols.length()").value(2))
                .andExpect(jsonPath("$.missingSymbols[1]").value("NVDA"));
    }

    @Test
    @DisplayName("GET /api/v1/market-data/quotes - normalizes and de-duplicates symbols")
    void getQuotesNormalizesAndDeduplicatesSymbols() throws Exception {
        update("AAPL", "187.25");

        mockMvc.perform(get("/api/v1/market-data/quotes").param("symbols", " aapl , AAPL,msft,MSFT "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes.length()").value(1))
                .andExpect(jsonPath("$.missingSymbols.length()").value(1))
                .andExpect(jsonPath("$.missingSymbols[0]").value("MSFT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "AAPL,", ",AAPL", "AAPL,,MSFT", "AAPL, "})
    @DisplayName("GET /api/v1/market-data/quotes - rejects blank symbol entries with 400")
    void getQuotesRejectsBlankSymbolEntries(String symbols) throws Exception {
        assertBadRequest(get("/api/v1/market-data/quotes").param("symbols", symbols));
    }

    @Test
    @DisplayName("GET /api/v1/market-data/quotes - rejects a missing symbols parameter with 400")
    void getQuotesRejectsMissingSymbolsParameter() throws Exception {
        assertBadRequest(get("/api/v1/market-data/quotes"));
    }

    @Test
    @DisplayName("GET /api/v1/market-data/quotes - accepts exactly 50 symbols")
    void getQuotesAcceptsFiftySymbols() throws Exception {
        mockMvc.perform(get("/api/v1/market-data/quotes").param("symbols", symbolList(50)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missingSymbols.length()").value(50));
    }

    @Test
    @DisplayName("GET /api/v1/market-data/quotes - rejects more than 50 symbols with 400")
    void getQuotesRejectsMoreThanFiftySymbols() throws Exception {
        assertBadRequest(get("/api/v1/market-data/quotes").param("symbols", symbolList(51)));
    }

    @Test
    @DisplayName("GET /api/v1/market-data/quotes - rejects a symbol longer than 20 characters with 400")
    void getQuotesRejectsOverLengthSymbol() throws Exception {
        assertBadRequest(get("/api/v1/market-data/quotes").param("symbols", "A".repeat(21)));
    }

    private void update(String symbol, String lastPrice) {
        marketDataService.update(new MarketDataEvent(symbol, new BigDecimal(lastPrice), null, null, "USD",
                Instant.parse("2026-05-01T12:00:00Z"), "yfinance"));
    }

    private static String symbolList(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "SYM" + i).collect(Collectors.joining(","));
    }

    private void assertBadRequest(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MKT-400"));
    }
}
