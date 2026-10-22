package com.leapvelocity.dto.response;

import com.leapvelocity.entities.Instrument;

public record InstrumentDto(
        Long id,
        String symbol,
        String name,
        String assetClass,
        String currency,
        boolean tradable,
        String sector
) {

    public InstrumentDto(Long id, String symbol, String name, String assetClass, String currency, boolean tradable) {
        this(id, symbol, name, assetClass, currency, tradable, "Other");
    }

    public static InstrumentDto from(Instrument instrument) {
        return new InstrumentDto(
                instrument.getId(),
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getAssetClass(),
                instrument.getCurrency(),
                instrument.isTradable(),
                instrument.getSector()
        );
    }
}
