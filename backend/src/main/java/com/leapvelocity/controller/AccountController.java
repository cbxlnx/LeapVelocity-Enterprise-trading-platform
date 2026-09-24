package com.leapvelocity.controller;

import com.leapvelocity.dto.response.AccountBalanceDto;
import com.leapvelocity.dto.response.AccountDto;
import com.leapvelocity.dto.response.OrderDto;
import com.leapvelocity.dto.response.PositionDto;
import com.leapvelocity.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for account management operations.
 * Provides endpoints for retrieving account details, balance, positions, and order history.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Retrieve account details by account ID.
     *
     * @param id The account ID
     * @return ResponseEntity with account details (HTTP 200)
     * @throws AccountNotFoundException if the account does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountDto> getAccount(@PathVariable Long id) {
        AccountDto account = accountService.getAccount(id);
        return ResponseEntity.ok(account);
    }

    /**
     * Retrieve account cash balance by account ID.
     *
     * @param id The account ID
     * @return ResponseEntity with account balance information (HTTP 200)
     * @throws AccountNotFoundException if the account does not exist
     */
    @GetMapping("/{id}/balance")
    public ResponseEntity<AccountBalanceDto> getBalance(@PathVariable Long id) {
        AccountBalanceDto balance = accountService.getBalance(id);
        return ResponseEntity.ok(balance);
    }

    /**
     * Retrieve all positions held by an account.
     *
     * @param id The account ID
     * @return ResponseEntity with list of positions (HTTP 200)
     * @throws AccountNotFoundException if the account does not exist
     */
    @GetMapping("/{id}/positions")
    public ResponseEntity<List<PositionDto>> getPositions(@PathVariable Long id) {
        List<PositionDto> positions = accountService.getPositions(id);
        return ResponseEntity.ok(positions);
    }

    /**
     * Retrieve order history for an account.
     *
     * @param id The account ID
     * @return ResponseEntity with list of orders (HTTP 200)
     * @throws AccountNotFoundException if the account does not exist
     */
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderDto>> getOrders(@PathVariable Long id) {
        List<OrderDto> orders = accountService.getOrders(id);
        return ResponseEntity.ok(orders);
    }
}
