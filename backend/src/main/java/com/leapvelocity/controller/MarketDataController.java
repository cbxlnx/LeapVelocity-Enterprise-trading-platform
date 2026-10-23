package com.leapvelocity.controller;

import com.leapvelocity.dto.response.BatchQuotesDto;
import com.leapvelocity.dto.response.QuoteDto;
import com.leapvelocity.exceptions.InvalidQuoteRequestException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * REST Controller for the latest market quotes. The frontend reads quotes only through trade-api.
 */
@Tag(name = "Market Data", description = "Latest quote per symbol, sourced from the market-data topic")
@RestController
@RequestMapping("/api/v1/market-data")
public class MarketDataController {

    private static final int MAX_SYMBOLS_PER_REQUEST = 50;
    private static final int MAX_SYMBOL_LENGTH = 20;

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

    @Operation(summary = "Get latest quotes for several symbols",
            description = "Symbols are trimmed, upper-cased and de-duplicated. Unknown symbols are listed in missingSymbols and still return HTTP 200. Use lastPrice for valuation and asOf to judge freshness.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Quotes retrieved; unknown symbols are listed in missingSymbols"),
            @ApiResponse(responseCode = "400", description = "symbols is missing, has a blank or over-length entry, or exceeds 50 entries")
    })
    @GetMapping("/quotes")
    public ResponseEntity<BatchQuotesDto> getQuotes(
            @Parameter(description = "Comma-separated ticker symbols, at most 50", example = "AAPL,MSFT")
            @RequestParam(required = false) String symbols) {
        MarketDataService.QuoteLookup lookup = marketDataService.latestQuotes(parseSymbols(symbols));
        List<QuoteDto> quotes = lookup.quotes().stream().map(QuoteDto::from).toList();
        return ResponseEntity.ok(new BatchQuotesDto(quotes, lookup.missingSymbols(), Instant.now()));
    }

    private static List<String> parseSymbols(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidQuoteRequestException("symbols query parameter is required");
        }
        List<String> symbols = Arrays.stream(raw.split(",", -1)).map(String::trim).toList();
        if (symbols.size() > MAX_SYMBOLS_PER_REQUEST) {
            throw new InvalidQuoteRequestException("At most " + MAX_SYMBOLS_PER_REQUEST + " symbols can be requested");
        }
        if (symbols.stream().anyMatch(symbol -> symbol.isEmpty() || symbol.length() > MAX_SYMBOL_LENGTH)) {
            throw new InvalidQuoteRequestException("Each symbol must contain between 1 and " + MAX_SYMBOL_LENGTH + " characters");
        }
        return symbols;
    }
}
