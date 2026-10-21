package com.leapvelocity.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.Locale;

/** RA-01 query contract. Null/omitted options receive defaults; invalid supplied values do not. */
@Schema(description = "Risk query options; fractions use 0.95 for 95%. Confidence/horizon configure all three VaR methods.")
public record RiskRequestDto(
        @Schema(defaultValue = "USD", allowableValues = {"USD"})
        @Pattern(regexp = "USD") String currency,

        @Schema(defaultValue = "1Y", allowableValues = {"1M", "3M", "6M", "1Y", "2Y"})
        @Pattern(regexp = "1M|3M|6M|1Y|2Y") String period,

        @Schema(defaultValue = "0.95", minimum = "0.90", maximum = "0.99")
        @DecimalMin("0.90") @DecimalMax("0.99") BigDecimal confidenceLevel,

        @Schema(defaultValue = "1", minimum = "1", maximum = "10", description = "Trading sessions; applies only to VaR")
        @Min(1) @Max(10) Integer holdingPeriodDays,

        @Schema(defaultValue = "SPY", description = "Known USD non-CASH benchmark; missing history only disables beta")
        @Pattern(regexp = "[A-Z0-9][A-Z0-9.^=-]{0,19}") String benchmark,

        @Schema(defaultValue = "7", minimum = "1", maximum = "30",
                description = "Maximum calendar-day age of the valuation/effective history end, inclusive")
        @Min(1) @Max(30) Integer maxPriceAgeDays
) {
    public RiskRequestDto {
        currency = normalize(currency, "USD");
        period = normalize(period, "1Y");
        confidenceLevel = confidenceLevel == null ? new BigDecimal("0.95") : confidenceLevel;
        holdingPeriodDays = holdingPeriodDays == null ? 1 : holdingPeriodDays;
        benchmark = normalize(benchmark, "SPY");
        maxPriceAgeDays = maxPriceAgeDays == null ? 7 : maxPriceAgeDays;
    }

    public static RiskRequestDto defaults() {
        return new RiskRequestDto(null, null, null, null, null, null);
    }

    private static String normalize(String value, String fallback) {
        return value == null ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }
}
