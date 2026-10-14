package com.leapvelocity.controller;

import com.leapvelocity.dto.response.QuoteDto;
import com.leapvelocity.exceptions.QuoteNotFoundException;
import com.leapvelocity.service.MarketDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for the latest market quotes. The frontend reads quotes only through trade-api.
 */
@Tag(name = "Market Data", description = "Latest quote per symbol, sourced from the market-data topic")
@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * Retrieve the latest quote for a symbol.
     *
     * @param symbol The ticker symbol
     * @return ResponseEntity with the latest quote (HTTP 200)
     * @throws QuoteNotFoundException if no quote has been received for the symbol
     */
    @Operation(summary = "Get latest quote", description = "Retrieve the most recent price for a symbol. Check asOf to judge how current the quote is.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Quote retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No quote received yet for the symbol")
    })
    @GetMapping("/quotes/{symbol}")
    public ResponseEntity<QuoteDto> getQuote(
            @Parameter(description = "Ticker symbol to retrieve the quote for", example = "AAPL")
            @PathVariable String symbol) {
        QuoteDto quote = marketDataService.latestQuote(symbol)
                .map(QuoteDto::from)
                .orElseThrow(() -> new QuoteNotFoundException(symbol));
        return ResponseEntity.ok(quote);
    }
}
