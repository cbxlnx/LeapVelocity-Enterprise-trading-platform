package com.leapvelocity.controller;

import com.leapvelocity.dto.request.PlaceOrderRequestDto;
import com.leapvelocity.dto.response.OrderDto;
import com.leapvelocity.entities.Order;
import com.leapvelocity.service.OrderExecutionService;
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
    @DeleteMapping("/{id}")
    public ResponseEntity<OrderDto> cancelOrder(@PathVariable UUID id) {
        Order cancelledOrder = orderExecutionService.cancelOrder(id);
        OrderDto response = OrderDto.from(cancelledOrder);
        return ResponseEntity.ok(response);
    }
}
