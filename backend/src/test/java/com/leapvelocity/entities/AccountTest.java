package com.leapvelocity.entities;

import com.leapvelocity.entities.enums.AccountStatus;
import com.leapvelocity.exceptions.InsufficientFundsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Account Entity")
class AccountTest {

    @Test
    @DisplayName("constructor initializes account defaults")
    void constructorInitializesDefaults() {
        Account account = new Account("ACC-001", "Ada Lovelace", new BigDecimal("1000.00"), AccountStatus.ACTIVE);

        assertEquals("ACC-001", account.getAccountId());
        assertEquals("Ada Lovelace", account.getHolderName());
        assertEquals(new BigDecimal("1000.00"), account.getCashBalance());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(0, account.getVersion());
        assertNotNull(account.getLastUpdated());
        assertTrue(account.isActive());
    }

    @Test
    @DisplayName("debit subtracts cash and updates timestamp")
    void debitSubtractsCash() {
        Account account = new Account("ACC-001", "Ada Lovelace", new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        LocalDateTime beforeDebit = account.getLastUpdated();

        account.debit(new BigDecimal("250.00"));

        assertEquals(new BigDecimal("750.00"), account.getCashBalance());
        assertTrue(!account.getLastUpdated().isBefore(beforeDebit));
    }

    @Test
    @DisplayName("debit rejects amounts greater than the balance")
    void debitRejectsInsufficientCash() {
        Account account = new Account("ACC-001", "Ada Lovelace", new BigDecimal("100.00"), AccountStatus.ACTIVE);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> account.debit(new BigDecimal("150.00"))
        );

        assertEquals("Insufficient cash balance", exception.getMessage());
        assertEquals(new BigDecimal("100.00"), account.getCashBalance());
    }

    @Test
    @DisplayName("credit adds cash and updates timestamp")
    void creditAddsCash() {
        Account account = new Account("ACC-001", "Ada Lovelace", new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        LocalDateTime beforeCredit = account.getLastUpdated();

        account.credit(new BigDecimal("125.50"));

        assertEquals(new BigDecimal("1125.50"), account.getCashBalance());
        assertTrue(!account.getLastUpdated().isBefore(beforeCredit));
    }

    @Test
    @DisplayName("credit and debit reject negative amounts")
    void creditAndDebitRejectNegativeAmounts() {
        Account account = new Account("ACC-001", "Ada Lovelace", new BigDecimal("1000.00"), AccountStatus.ACTIVE);

        assertThrows(IllegalArgumentException.class, () -> account.credit(new BigDecimal("-1.00")));
        assertThrows(IllegalArgumentException.class, () -> account.debit(new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("debit insufficient funds throws exception and preserves balance")
    void debitInsufficientFunds() {
        Account account = new Account("ACC-001", "Alice", new BigDecimal("100.00"), AccountStatus.ACTIVE);
        
        // Account has $100, trying to debit $150
        assertThrows(IllegalArgumentException.class, () -> account.debit(new BigDecimal("150.00")));
        
        // Balance unchanged after failed debit
        assertEquals(new BigDecimal("100.00"), account.getCashBalance());
    }

    @Test
    @DisplayName("isActive returns true for ACTIVE status")
    void isActiveReturnsTrueForActiveStatus() {
        Account account = new Account("ACC-001", "Alice", new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        
        assertTrue(account.isActive());
    }

    @Test
    @DisplayName("isActive returns false for SUSPENDED status")
    void isActiveReturnsFalseForSuspendedStatus() {
        Account account = new Account("ACC-001", "Alice", new BigDecimal("1000.00"), AccountStatus.SUSPENDED);
        
        assertFalse(account.isActive());
    }

    @Test
    @DisplayName("isActive returns false for CLOSED status")
    void isActiveReturnsFalseForClosedStatus() {
        Account account = new Account("ACC-001", "Alice", new BigDecimal("1000.00"), AccountStatus.CLOSED);
        
        assertFalse(account.isActive());
    }
}
