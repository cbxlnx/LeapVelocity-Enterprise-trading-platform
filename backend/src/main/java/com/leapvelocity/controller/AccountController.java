package com.leapvelocity.controller;

import com.leapvelocity.dto.response.AccountBalanceDto;
import com.leapvelocity.dto.response.AccountDto;
import com.leapvelocity.dto.response.OrderDto;
import com.leapvelocity.dto.response.PositionDto;
import com.leapvelocity.service.AccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Accounts", description = "Account management endpoints for retrieving account details, balance, positions, and order history")
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
    @Operation(summary = "Get account details", description = "Retrieve complete account information including holder name, cash balance, and current status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found with the provided ID")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AccountDto> getAccount(
            @Parameter(description = "Account ID to retrieve", example = "1")
            @PathVariable Long id) {
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
    @Operation(summary = "Get account balance", description = "Retrieve the current available cash balance for trading in the account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{id}/balance")
    public ResponseEntity<AccountBalanceDto> getBalance(
            @Parameter(description = "Account ID to retrieve balance for", example = "1")
            @PathVariable Long id) {
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
    @Operation(summary = "Get account positions", description = "Retrieve all holdings (positions) currently owned by the account, including quantity and average cost per share")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Positions retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{id}/positions")
    public ResponseEntity<List<PositionDto>> getPositions(
            @Parameter(description = "Account ID to retrieve positions for", example = "1")
            @PathVariable Long id) {
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
    @Operation(summary = "Get account order history", description = "Retrieve all orders placed by the account, including filled, rejected, and cancelled orders")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderDto>> getOrders(
            @Parameter(description = "Account ID to retrieve order history for", example = "1")
            @PathVariable Long id) {
        List<OrderDto> orders = accountService.getOrders(id);
        return ResponseEntity.ok(orders);
    }
}
