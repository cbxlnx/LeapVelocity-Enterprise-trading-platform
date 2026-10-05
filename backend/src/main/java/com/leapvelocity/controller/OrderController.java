package com.leapvelocity.controller;

import com.leapvelocity.dto.request.PlaceOrderRequestDto;
import com.leapvelocity.dto.response.OrderDto;
import com.leapvelocity.entities.Order;
import com.leapvelocity.service.OrderExecutionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for order management operations.
 * Provides endpoints for placing and managing customer orders.
 */
@Tag(name = "Orders", description = "Order placement and management endpoints for trading operations")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderExecutionService orderExecutionService;

    public OrderController(OrderExecutionService orderExecutionService) {
        this.orderExecutionService = orderExecutionService;
    }

    /**
     * Place a new order for a trading account.
     *
     * @param request The order details (accountId, symbol, side, quantity, price, idempotencyKey)
     * @return ResponseEntity with the created order details (HTTP 201)
     * @throws AccountNotFoundException if the account does not exist
     * @throws AccountNotActiveException if the account is not active
     * @throws InstrumentNotFoundException if the instrument is not tradable
     * @throws InsufficientFundsException if the account has insufficient cash for BUY orders
     * @throws InsufficientHoldingsException if the account lacks sufficient shares for SELL orders
     * @throws DuplicateOrderException if an order with the same idempotency key already exists
     */
    @Operation(summary = "Place a new order", description = "Submit a buy or sell order for a trading account. The order is validated and executed immediately if all conditions are met.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Order placed and executed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid order data (missing fields or validation failed)"),
            @ApiResponse(responseCode = "404", description = "Account or instrument not found"),
            @ApiResponse(responseCode = "409", description = "Duplicate order or insufficient funds/holdings"),
            @ApiResponse(responseCode = "422", description = "Order rejected (account inactive, instrument not tradable)")
    })
    @PostMapping
    public ResponseEntity<OrderDto> placeOrder(@Valid @RequestBody PlaceOrderRequestDto request) {
        // Convert DTO to Order entity
        Order order = new Order(
                request.accountId(),
                request.symbol(),
                request.side(),
                request.quantity(),
                request.price(),
                request.idempotencyKey()
        );

        // Execute the order through the service
        Order executedOrder = orderExecutionService.placeOrder(order);

        // Convert result back to DTO and return with HTTP 201 Created
        OrderDto response = OrderDto.from(executedOrder);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Cancel an existing order by order ID.
     *
     * @param id The order ID (UUID)
     * @return ResponseEntity with the cancelled order details (HTTP 200)
     * @throws OrderNotFoundException if the order does not exist
     * @throws IllegalArgumentException if the order has already been filled or previously cancelled
     */
    @Operation(summary = "Cancel an order", description = "Cancel a pending order that hasn't been filled yet. Only orders in NEW status can be cancelled.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order cancelled successfully"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order cannot be cancelled (already filled, rejected, or previously cancelled)")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<OrderDto> cancelOrder(
            @Parameter(description = "Order ID (UUID) to cancel", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID id) {
        Order cancelledOrder = orderExecutionService.cancelOrder(id);
        OrderDto response = OrderDto.from(cancelledOrder);
        return ResponseEntity.ok(response);
    }
}
