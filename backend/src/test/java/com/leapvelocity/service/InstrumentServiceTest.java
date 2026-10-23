package com.leapvelocity.service;

import com.leapvelocity.dto.response.InstrumentDto;
import com.leapvelocity.entities.Instrument;
import com.leapvelocity.repository.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("InstrumentService")
class InstrumentServiceTest {

    private InstrumentRepository instrumentRepository;
    private InstrumentService service;

    @BeforeEach
    void setUp() {
        instrumentRepository = mock(InstrumentRepository.class);
        service = new InstrumentService(instrumentRepository);
    }

    @Test
    @DisplayName("search returns empty without querying when the query is blank or null")
    void searchReturnsEmptyForBlankQuery() {
        assertTrue(service.search("   ", 10).isEmpty());
        assertTrue(service.search(null, 10).isEmpty());

        verifyNoInteractions(instrumentRepository);
    }

    @Test
    @DisplayName("search trims the query and maps instruments to DTOs")
    void searchTrimsQueryAndMapsInstruments() {
        Instrument apple = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);
        when(instrumentRepository.searchByPrefix(eq("AP"), any(Pageable.class))).thenReturn(List.of(apple));

        List<InstrumentDto> result = service.search("  AP ", 10);

        assertEquals(1, result.size());
        assertEquals("AAPL", result.get(0).symbol());
        assertEquals("Apple Inc.", result.get(0).name());
        assertTrue(result.get(0).tradable());
    }

    @Test
    @DisplayName("search clamps the limit to between 1 and 50")
    void searchClampsLimitToRange() {
        when(instrumentRepository.searchByPrefix(eq("A"), any(Pageable.class))).thenReturn(List.of());

        service.search("A", 0);
        service.search("A", 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(instrumentRepository, times(2)).searchByPrefix(eq("A"), captor.capture());
        assertEquals(1, captor.getAllValues().get(0).getPageSize());
        assertEquals(50, captor.getAllValues().get(1).getPageSize());
    }
}
