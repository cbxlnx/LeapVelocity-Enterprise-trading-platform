package com.leapvelocity.controller;

import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.messaging.MarketDataEvent;
import com.leapvelocity.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

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
}
