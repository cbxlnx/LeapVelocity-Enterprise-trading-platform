package com.leapvelocity.service;

import com.leapvelocity.dto.response.InstrumentDto;
import com.leapvelocity.repository.InstrumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InstrumentService {

    private static final int MAX_LIMIT = 50;

    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public List<InstrumentDto> getTradableInstruments() {
        return instrumentRepository.findAll().stream()
                .filter(instrument -> instrument.isTradable())
                .map(InstrumentDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InstrumentDto> search(String query, int limit) {
        String prefix = query == null ? "" : query.trim();
        if (prefix.isEmpty()) {
            return List.of();
        }
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return instrumentRepository.searchByPrefix(prefix, PageRequest.of(0, pageSize)).stream()
                .map(InstrumentDto::from)
                .toList();
    }
}
