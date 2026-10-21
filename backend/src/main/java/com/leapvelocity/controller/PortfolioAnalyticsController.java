package com.leapvelocity.controller;

import com.leapvelocity.dto.request.RiskRequestDto;
import com.leapvelocity.dto.response.PortfolioRiskDto;
import com.leapvelocity.dto.response.PortfolioSummaryDto;
import com.leapvelocity.security.AccountAccessAuthorizer;
import com.leapvelocity.service.PortfolioAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/accounts/{id}/analytics")
@Tag(name = "Portfolio Analytics")
public class PortfolioAnalyticsController {
    private static final Set<String> RISK_QUERY_OPTIONS = Set.of("currency", "period", "confidenceLevel",
            "holdingPeriodDays", "benchmark", "maxPriceAgeDays");
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

    @Operation(summary = "Get portfolio risk assessment", description =
            "Calculate annualised volatility, maximum drawdown, benchmark beta and historical VaR using current-allocation "
                    + "buy-and-hold returns. Units, dates, counts and availability reasons accompany each metric. "
                    + "Historical VaR compounds overlapping session windows and reports an estimated percent/USD loss. "
                    + "Parametric and Monte Carlo VaR fit a normal distribution to compounded holding-period returns; "
                    + "Monte Carlo samples reproducible outcomes. All methods use the same invested holdings value.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Risk assessment with explicit availability and implementation states"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Account belongs to another user"),
            @ApiResponse(responseCode = "404", description = "Account not found"),
            @ApiResponse(responseCode = "422", description = "Invalid ID/options or unsupported benchmark"),
            @ApiResponse(responseCode = "500", description = "Infrastructure/programming failure, including risk-service timeout or invalid response")
    })
    @GetMapping("/risk")
    public PortfolioRiskDto getRisk(@PathVariable Long id, @Valid @ModelAttribute @ParameterObject RiskRequestDto options) {
        if (id == null || id <= 0) throw new IllegalArgumentException("A positive account id is required");
        AccountAccessAuthorizer.requireOwnAccountOrAdmin(id);
        return analytics.getRisk(id, options);
    }

    @InitBinder("riskRequestDto")
    void configureRiskBinding(WebDataBinder binder, WebRequest request) {
        // Bind the immutable request record through its constructor.
        binder.setDeclarativeBinding(true);
        request.getParameterMap().forEach((name, values) -> {
            if (!RISK_QUERY_OPTIONS.contains(name)) throw new IllegalArgumentException("Unknown risk option: " + name);
            if (values.length != 1) throw new IllegalArgumentException("Supply risk option once: " + name);
            if (values[0] == null || values[0].isBlank()) {
                throw new IllegalArgumentException("Risk option must not be blank: " + name);
            }
        });
    }
}
