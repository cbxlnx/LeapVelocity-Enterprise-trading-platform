package com.leapvelocity.controller;

import com.leapvelocity.dto.response.HistoricalPricesDto;
import com.leapvelocity.service.HistoricalPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/market-data/prices")
@Tag(name = "Market Data", description = "Latest quotes and stored historical prices")
public class HistoricalPriceController {
    private final HistoricalPriceService historicalPriceService;

    public HistoricalPriceController(HistoricalPriceService historicalPriceService) {
        this.historicalPriceService = historicalPriceService;
    }

    @Operation(summary = "Get daily historical prices", description =
            "Read stored closing prices in ascending trading-date order. Both from and to are inclusive "
                    + "and required. A known instrument with no data returns an empty prices list.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "History retrieved, possibly empty"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Unknown instrument"),
            @ApiResponse(responseCode = "422", description = "Missing, invalid or reversed dates")
    })
    @GetMapping("/{symbol}")
    public HistoricalPricesDto getPrices(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return historicalPriceService.getPrices(symbol, from, to);
    }
}
