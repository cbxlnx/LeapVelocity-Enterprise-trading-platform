package com.leapvelocity.dto.response;

import com.leapvelocity.entities.Account;
import com.leapvelocity.entities.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountDto(
        Long id,
        String accountId,
        String holderName,
        BigDecimal cashBalance,
        AccountStatus status,
    Integer version,
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
