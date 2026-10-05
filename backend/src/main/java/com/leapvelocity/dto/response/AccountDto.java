package com.leapvelocity.dto.response;

import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.enums.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Trading account details with balance and status information")
public record AccountDto(
        @Schema(description = "Internal unique identifier for the account", example = "1")
        Long id,
        
        @Schema(description = "User-facing account identifier/number", example = "ACC-001")
        String accountId,
        
        @Schema(description = "Full name of the account holder", example = "John Smith")
        String holderName,
        
        @Schema(description = "Current cash balance available for trading", example = "9500.50")
        BigDecimal cashBalance,
        
        @Schema(description = "Current status: ACTIVE, SUSPENDED, or CLOSED", example = "ACTIVE")
        AccountStatus status,
        
        @Schema(description = "Optimistic locking version number", example = "5")
        Integer version,
        
        @Schema(description = "Timestamp of last update", example = "2026-09-24T14:30:00")
        LocalDateTime lastUpdated
) {

    public static AccountDto from(Account account) {
        return new AccountDto(
                account.getId(),
                account.getAccountId(),
                account.getHolderName(),
                account.getCashBalance(),
                account.getStatus(),
                account.getVersion(),
                account.getLastUpdated()
        );
    }
}
