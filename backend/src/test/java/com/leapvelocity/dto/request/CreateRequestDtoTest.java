package com.leapvelocity.dto.request;

import com.leapvelocity.entities.enums.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("Create request DTOs")
class CreateRequestDtoTest {

    @Test
    @DisplayName("create account request stores constructor values")
    void createAccountRequestStoresConstructorValues() {
        CreateAccountRequestDto dto = new CreateAccountRequestDto(
                "ACC-100",
                "Grace Hopper",
                new BigDecimal("2500.00"),
                AccountStatus.ACTIVE
        );

        assertEquals("ACC-100", dto.accountId());
        assertEquals("Grace Hopper", dto.holderName());
        assertEquals(new BigDecimal("2500.00"), dto.cashBalance());
        assertEquals(AccountStatus.ACTIVE, dto.status());
    }

    @Test
    @DisplayName("create instrument request stores constructor values")
    void createInstrumentRequestStoresConstructorValues() {
        CreateInstrumentRequestDto dto = new CreateInstrumentRequestDto(
                "VWRL",
                "Vanguard FTSE All-World",
                "ETF",
                "GBP",
                false
        );

        assertEquals("VWRL", dto.symbol());
        assertEquals("Vanguard FTSE All-World", dto.name());
        assertEquals("ETF", dto.assetClass());
        assertEquals("GBP", dto.currency());
        assertFalse(dto.tradable());
    }

    @Test
    @DisplayName("create position request stores constructor values")
    void createPositionRequestStoresConstructorValues() {
        CreatePositionRequestDto dto = new CreatePositionRequestDto(
                42L,
                "NVDA",
                new BigDecimal("12.50"),
                new BigDecimal("905.10")
        );

        assertEquals(42L, dto.accountId());
        assertEquals("NVDA", dto.symbol());
        assertEquals(new BigDecimal("12.50"), dto.quantity());
        assertEquals(new BigDecimal("905.10"), dto.averageCost());
    }
}