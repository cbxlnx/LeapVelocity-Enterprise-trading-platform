package com.leapvelocity.dto.response;

import com.leapvelocity.entities.Position;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Holdings position for an instrument owned by an account")
public record PositionDto(
        @Schema(description = "Internal unique identifier for the position", example = "1")
        Long id,
        
        @Schema(description = "Account ID that owns this position", example = "1")
        Long accountId,
        
        @Schema(description = "Trading symbol of the held instrument", example = "AAPL")
        String symbol,
        
        @Schema(description = "Number of shares held", example = "50")
        BigDecimal quantity,
        
        @Schema(description = "Average cost per share for P&L calculation", example = "145.30")
        BigDecimal averageCost
) {

    public static PositionDto from(Position position) {
        return new PositionDto(
                position.getId(),
                position.getAccountId(),
                position.getSymbol(),
                position.getQuantity(),
                position.getAverageCost()
        );
    }
}
