package com.leapvelocity.dto;

import com.leapvelocity.entities.Position;

import java.math.BigDecimal;

public record PositionDto(
        Long id,
        Long accountId,
        String symbol,
        BigDecimal quantity,
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
