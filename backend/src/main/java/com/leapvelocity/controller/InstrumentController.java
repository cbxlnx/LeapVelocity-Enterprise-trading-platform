package com.leapvelocity.controller;

import com.leapvelocity.dto.response.InstrumentDto;
import com.leapvelocity.service.InstrumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for instrument lookup, used by the order entry autocomplete.
 */
@Tag(name = "Instruments", description = "Instrument lookup endpoints")
@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @Operation(summary = "List instruments", description = "Retrieve all tradable instruments registered in the database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tradable instruments retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<List<InstrumentDto>> getInstruments() {
        return ResponseEntity.ok(instrumentService.getTradableInstruments());
    }

    /**
     * Search instruments by symbol or name prefix.
     *
     * @param query The text typed so far; blank returns an empty list
     * @param limit Maximum number of results, clamped to 1-50
     * @return ResponseEntity with matching instruments (HTTP 200)
     */
    @Operation(summary = "Search instruments", description = "Autocomplete instruments whose symbol or name starts with the query text")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Matching instruments retrieved successfully")
    })
    @GetMapping("/search")
    public ResponseEntity<List<InstrumentDto>> search(
            @Parameter(description = "Symbol or name prefix to match", example = "AP")
            @RequestParam(name = "q", defaultValue = "") String query,
            @Parameter(description = "Maximum number of results (1-50)", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(instrumentService.search(query, limit));
    }
}
