package com.leapvelocity.controller;

import com.leapvelocity.dto.response.InstrumentDto;
import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.service.InstrumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("InstrumentController")
class InstrumentControllerTest {

    private InstrumentService instrumentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        instrumentService = mock(InstrumentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new InstrumentController(instrumentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

        @Test
        @DisplayName("GET /api/v1/instruments - returns tradable instruments from the service")
        void getInstrumentsReturnsTradableInstruments() throws Exception {
        when(instrumentService.getTradableInstruments()).thenReturn(
            List.of(new InstrumentDto(1L, "AAPL", "Apple Inc.", "EQUITY", "USD", true)));

        mockMvc.perform(get("/api/v1/instruments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].symbol").value("AAPL"));

        verify(instrumentService).getTradableInstruments();
        }

    @Test
    @DisplayName("GET /api/v1/instruments/search - uses empty query and limit 10 when params are missing")
    void searchUsesDefaultsWhenParamsMissing() throws Exception {
        when(instrumentService.search("", 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/instruments/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(instrumentService).search("", 10);
    }

    @Test
    @DisplayName("GET /api/v1/instruments/search - passes q and limit to the service")
    void searchPassesQueryAndLimitToService() throws Exception {
        when(instrumentService.search("AP", 5)).thenReturn(
                List.of(new InstrumentDto(1L, "AAPL", "Apple Inc.", "EQUITY", "USD", true)));

        mockMvc.perform(get("/api/v1/instruments/search").param("q", "AP").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"));

        verify(instrumentService).search("AP", 5);
    }
}
