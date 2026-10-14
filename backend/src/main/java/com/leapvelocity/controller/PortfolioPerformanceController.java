package com.leapvelocity.controller;

import com.leapvelocity.dto.response.PortfolioPerformanceDto;
import com.leapvelocity.security.AccountAccessAuthorizer;
import com.leapvelocity.service.PortfolioPerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{id}/analytics")
@Tag(name = "Portfolio Analytics")
public class PortfolioPerformanceController {
    private final PortfolioPerformanceService performance;

    public PortfolioPerformanceController(PortfolioPerformanceService performance) { this.performance = performance; }

    @Operation(summary = "Simulate current portfolio allocation against SPY", description =
            "Hypothetical buy-and-hold, excluding cash, using common adjusted-price dates. "
                    + "Both indices start at 100. Returns are percentage points (2 means 2%).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simulation: READY, EMPTY or UNAVAILABLE"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Account belongs to another user"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "422", description = "Unsupported period")
    })
    @GetMapping("/performance")
    public PortfolioPerformanceDto getPerformance(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "1Y") String period) {
        AccountAccessAuthorizer.requireOwnAccountOrAdmin(id);
        return performance.getPerformance(id, period);
    }
}
