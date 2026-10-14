package com.leapvelocity.controller;

import com.leapvelocity.dto.response.PortfolioSummaryDto;
import com.leapvelocity.security.AccountAccessAuthorizer;
import com.leapvelocity.service.PortfolioAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{id}/analytics")
@Tag(name = "Portfolio Analytics")
public class PortfolioAnalyticsController {
    private final PortfolioAnalyticsService analytics;

    public PortfolioAnalyticsController(PortfolioAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Operation(summary = "Get portfolio valuation and allocation", description =
            "Value current holdings at the latest common completed trading date. Cash is separate; "
                    + "weight is a fraction of holdings value. Missing prices return UNAVAILABLE, issues and null valuations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary: READY, EMPTY or UNAVAILABLE"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Account belongs to another user"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/summary")
    public PortfolioSummaryDto getSummary(@PathVariable Long id) {
        AccountAccessAuthorizer.requireOwnAccountOrAdmin(id);
        return analytics.getSummary(id);
    }
}
