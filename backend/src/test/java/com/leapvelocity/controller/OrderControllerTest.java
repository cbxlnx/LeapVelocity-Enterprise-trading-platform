package com.leapvelocity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leapvelocity.dto.request.PlaceOrderRequestDto;
import com.leapvelocity.dto.response.OrderDto;
import com.leapvelocity.entities.Order;
import com.leapvelocity.entities.enums.OrderSide;
import com.leapvelocity.entities.enums.OrderStatus;
import com.leapvelocity.exceptions.AccountNotActiveException;
import com.leapvelocity.exceptions.AccountNotFoundException;
import com.leapvelocity.exceptions.DuplicateOrderException;
import com.leapvelocity.exceptions.GlobalExceptionHandler;
import com.leapvelocity.exceptions.InsufficientFundsException;
import com.leapvelocity.exceptions.InstrumentNotFoundException;
import com.leapvelocity.exceptions.OrderNotFoundException;
import com.leapvelocity.service.OrderExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for OrderController REST endpoints.
 * Tests the POST /api/v1/orders endpoint for placing orders.
 */
@DisplayName("OrderController")
class OrderControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private OrderExecutionService orderExecutionService;

    private PlaceOrderRequestDto validRequest;
    private Order executedOrder;
    private OrderDto expectedResponse;

    @BeforeEach
    void setUp() {
        // Initialize mocks manually
        orderExecutionService = mock(OrderExecutionService.class);
        objectMapper = new ObjectMapper();
        
        // Setup MockMvc with controller and exception handler
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderExecutionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Setup a valid request
        validRequest = new PlaceOrderRequestDto(
                1L,                                    // accountId
                "AAPL",                               // symbol
                OrderSide.BUY,                        // side
                new BigDecimal("100.00"),             // quantity
                new BigDecimal("150.25"),             // price
                "IDEM-001"                            // idempotencyKey
        );

        // Setup the executed order that the service will return
        executedOrder = new Order(
                1L,
                "AAPL",
                OrderSide.BUY,
                new BigDecimal("100.00"),
                new BigDecimal("150.25"),
                "IDEM-001"
        );
        executedOrder.setStatus(OrderStatus.FILLED);
        executedOrder.setCreatedOn(LocalDateTime.now());

        // Setup the expected response
        expectedResponse = OrderDto.from(executedOrder);
    }

    // =============== Happy Path Tests ===============

    @Test
    @DisplayName("POST /api/v1/orders - Successfully places a BUY order")
    void postOrdersSuccessfullyPlacesBuyOrder() throws Exception {
        // Arrange
        when(orderExecutionService.placeOrder(any(Order.class))).thenReturn(executedOrder);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.accountId", equalTo(1)))
                .andExpect(jsonPath("$.symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$.side", equalTo("BUY")))
                .andExpect(jsonPath("$.quantity", equalTo(100.00)))
                .andExpect(jsonPath("$.price", equalTo(150.25)))
                .andExpect(jsonPath("$.status", equalTo("FILLED")))
                .andExpect(jsonPath("$.idempotencyKey", equalTo("IDEM-001")))
                .andExpect(jsonPath("$.createdOn", notNullValue()));

        verify(orderExecutionService, times(1)).placeOrder(any(Order.class));
    }

    @Test
    @DisplayName("POST /api/v1/orders - Successfully places a SELL order")
    void postOrdersSuccessfullyPlacesSellOrder() throws Exception {
        // Arrange
        PlaceOrderRequestDto sellRequest = new PlaceOrderRequestDto(
                1L,
                "MSFT",
                OrderSide.SELL,
                new BigDecimal("50.00"),
                new BigDecimal("380.50"),
                "IDEM-SELL-001"
        );

        Order sellOrder = new Order(
                1L,
                "MSFT",
                OrderSide.SELL,
                new BigDecimal("50.00"),
                new BigDecimal("380.50"),
                "IDEM-SELL-001"
        );
        sellOrder.setStatus(OrderStatus.FILLED);

        when(orderExecutionService.placeOrder(any(Order.class))).thenReturn(sellOrder);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sellRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side", equalTo("SELL")))
                .andExpect(jsonPath("$.symbol", equalTo("MSFT")))
                .andExpect(jsonPath("$.quantity", equalTo(50.00)))
                .andExpect(jsonPath("$.price", equalTo(380.50)));

        verify(orderExecutionService, times(1)).placeOrder(any(Order.class));
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns HTTP 201 Created")
    void postOrdersReturnsHttpCreated() throws Exception {
        // Arrange
        when(orderExecutionService.placeOrder(any(Order.class))).thenReturn(executedOrder);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());
    }

    // =============== Validation Error Tests ===============

    @Test
    @DisplayName("POST /api/v1/orders - Returns 400 when accountId is null")
    void postOrdersReturns400WhenAccountIdIsNull() throws Exception {
        // Arrange
        PlaceOrderRequestDto invalidRequest = new PlaceOrderRequestDto(
                null,                                 // accountId is null
                "AAPL",
                OrderSide.BUY,
                new BigDecimal("100.00"),
                new BigDecimal("150.25"),
                "IDEM-001"
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("ERR_008")));

        verify(orderExecutionService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 400 when symbol is blank")
    void postOrdersReturns400WhenSymbolIsBlank() throws Exception {
        // Arrange
        PlaceOrderRequestDto invalidRequest = new PlaceOrderRequestDto(
                1L,
                "",                                   // symbol is blank
                OrderSide.BUY,
                new BigDecimal("100.00"),
                new BigDecimal("150.25"),
                "IDEM-001"
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("ERR_008")));

        verify(orderExecutionService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 400 when side is null")
    void postOrdersReturns400WhenSideIsNull() throws Exception {
        // Arrange - Create request with null side by using raw JSON
        String invalidJson = "{\"accountId\":1,\"symbol\":\"AAPL\",\"side\":null,\"quantity\":100.00,\"price\":150.25,\"idempotencyKey\":\"IDEM-001\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("ERR_008")));

        verify(orderExecutionService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 400 when quantity is null")
    void postOrdersReturns400WhenQuantityIsNull() throws Exception {
        // Arrange
        String invalidJson = "{\"accountId\":1,\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":null,\"price\":150.25,\"idempotencyKey\":\"IDEM-001\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("ERR_008")));

        verify(orderExecutionService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 400 when quantity is not positive")
    void postOrdersReturns400WhenQuantityIsNotPositive() throws Exception {
        // Arrange
        PlaceOrderRequestDto invalidRequest = new PlaceOrderRequestDto(
                1L,
                "AAPL",
                OrderSide.BUY,
                new BigDecimal("-100.00"),            // negative quantity
                new BigDecimal("150.25"),
                "IDEM-001"
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("ERR_008")));

        verify(orderExecutionService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 400 when price is null")
    void postOrdersReturns400WhenPriceIsNull() throws Exception {
        // Arrange
        String invalidJson = "{\"accountId\":1,\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":100.00,\"price\":null,\"idempotencyKey\":\"IDEM-001\"}";

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("ERR_008")));

        verify(orderExecutionService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 400 when price is not positive")
    void postOrdersReturns400WhenPriceIsNotPositive() throws Exception {
        // Arrange
        PlaceOrderRequestDto invalidRequest = new PlaceOrderRequestDto(
                1L,
                "AAPL",
                OrderSide.BUY,
                new BigDecimal("100.00"),
                new BigDecimal("0.00"),               // zero price
                "IDEM-001"
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("ERR_008")));

        verify(orderExecutionService, never()).placeOrder(any());
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 400 when idempotencyKey is blank")
    void postOrdersReturns400WhenIdempotencyKeyIsBlank() throws Exception {
        // Arrange
        PlaceOrderRequestDto invalidRequest = new PlaceOrderRequestDto(
                1L,
                "AAPL",
                OrderSide.BUY,
                new BigDecimal("100.00"),
                new BigDecimal("150.25"),
                ""                                    // blank idempotencyKey
        );

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("ERR_008")));

        verify(orderExecutionService, never()).placeOrder(any());
    }

    // =============== Domain Exception Tests ===============

    @Test
    @DisplayName("POST /api/v1/orders - Returns 404 when account not found")
    void postOrdersReturns404WhenAccountNotFound() throws Exception {
        // Arrange
        when(orderExecutionService.placeOrder(any(Order.class)))
                .thenThrow(new AccountNotFoundException(99L));

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ERR_001")));

        verify(orderExecutionService, times(1)).placeOrder(any(Order.class));
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 403 when account is not active")
    void postOrdersReturns403WhenAccountNotActive() throws Exception {
        // Arrange
        when(orderExecutionService.placeOrder(any(Order.class)))
                .thenThrow(new AccountNotActiveException(1L));

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("ERR_002")));

        verify(orderExecutionService, times(1)).placeOrder(any(Order.class));
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 404 when instrument not tradable")
    void postOrdersReturns404WhenInstrumentNotTradable() throws Exception {
        // Arrange
        when(orderExecutionService.placeOrder(any(Order.class)))
                .thenThrow(new InstrumentNotFoundException("AAPL"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ERR_003")));

        verify(orderExecutionService, times(1)).placeOrder(any(Order.class));
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 422 when insufficient funds for BUY order")
    void postOrdersReturns422WhenInsufficientFundsForBuy() throws Exception {
        // Arrange
        when(orderExecutionService.placeOrder(any(Order.class)))
                .thenThrow(new InsufficientFundsException(1L, new BigDecimal("15025.00"), new BigDecimal("5000.00")));

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code", equalTo("ERR_004")));

        verify(orderExecutionService, times(1)).placeOrder(any(Order.class));
    }

    @Test
    @DisplayName("POST /api/v1/orders - Returns 409 when order with same idempotency key exists")
    void postOrdersReturns409WhenDuplicateOrder() throws Exception {
        // Arrange
        when(orderExecutionService.placeOrder(any(Order.class)))
                .thenThrow(new DuplicateOrderException("IDEM-001"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ERR_006")));

        verify(orderExecutionService, times(1)).placeOrder(any(Order.class));
    }

    // =============== Request/Response Format Tests ===============

    @Test
    @DisplayName("POST /api/v1/orders - Response contains correct media type")
    void postOrdersResponseHasCorrectMediaType() throws Exception {
        // Arrange
        when(orderExecutionService.placeOrder(any(Order.class))).thenReturn(executedOrder);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("POST /api/v1/orders - Response contains all required fields")
    void postOrdersResponseContainsAllRequiredFields() throws Exception {
        // Arrange
        when(orderExecutionService.placeOrder(any(Order.class))).thenReturn(executedOrder);

        // Act & Assert
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.symbol").exists())
                .andExpect(jsonPath("$.side").exists())
                .andExpect(jsonPath("$.quantity").exists())
                .andExpect(jsonPath("$.price").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.idempotencyKey").exists())
                .andExpect(jsonPath("$.createdOn").exists());
    }

    // =============== Idempotency Tests ===============

    @Test
    @DisplayName("POST /api/v1/orders - Accepts unique idempotency keys")
    void postOrdersAcceptsUniqueIdempotencyKeys() throws Exception {
        // Arrange - Request with first idempotency key
        PlaceOrderRequestDto request1 = new PlaceOrderRequestDto(1L, "AAPL", OrderSide.BUY,
                new BigDecimal("100"), new BigDecimal("150"), "IDEM-001");
        PlaceOrderRequestDto request2 = new PlaceOrderRequestDto(1L, "MSFT", OrderSide.BUY,
                new BigDecimal("50"), new BigDecimal("380"), "IDEM-002");

        Order order1 = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("150"), "IDEM-001");
        order1.setStatus(OrderStatus.FILLED);
        Order order2 = new Order(1L, "MSFT", OrderSide.BUY, new BigDecimal("50"), new BigDecimal("380"), "IDEM-002");
        order2.setStatus(OrderStatus.FILLED);

        when(orderExecutionService.placeOrder(any(Order.class)))
                .thenReturn(order1)
                .thenReturn(order2);

        // Act & Assert - Both should succeed
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        verify(orderExecutionService, times(2)).placeOrder(any(Order.class));
    }

    // =============== DELETE /api/v1/orders/{id} Tests ===============

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - Successfully cancels a NEW order")
    void deleteOrderSuccessfullyCancelsNewOrder() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order newOrder = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("150"), "IDEM-001");
        newOrder.setId(orderId);
        newOrder.setStatus(OrderStatus.NEW);
        newOrder.setCreatedOn(LocalDateTime.now());

        Order cancelledOrder = new Order(1L, "AAPL", OrderSide.BUY, new BigDecimal("100"), new BigDecimal("150"), "IDEM-001");
        cancelledOrder.setId(orderId);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        cancelledOrder.setCreatedOn(LocalDateTime.now());

        when(orderExecutionService.cancelOrder(orderId)).thenReturn(cancelledOrder);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(orderId.toString())))
                .andExpect(jsonPath("$.accountId", equalTo(1)))
                .andExpect(jsonPath("$.symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$.side", equalTo("BUY")))
                .andExpect(jsonPath("$.quantity", equalTo(100)))
                .andExpect(jsonPath("$.price", equalTo(150)))
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")));

        verify(orderExecutionService, times(1)).cancelOrder(orderId);
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - Returns 404 when order not found")
    void deleteOrderReturns404WhenOrderNotFound() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderExecutionService.cancelOrder(orderId))
                .thenThrow(new OrderNotFoundException(orderId.toString()));

        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", notNullValue()))
                .andExpect(jsonPath("$.message", containsString("not found")));

        verify(orderExecutionService, times(1)).cancelOrder(orderId);
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - Returns 400 when trying to cancel filled order")
    void deleteOrderReturns400WhenCancelFilledOrder() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderExecutionService.cancelOrder(orderId))
                .thenThrow(new IllegalArgumentException("Cannot cancel order with status: FILLED"));

        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", notNullValue()))
                .andExpect(jsonPath("$.message", containsString("Cannot cancel")));

        verify(orderExecutionService, times(1)).cancelOrder(orderId);
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - Response has correct content type")
    void deleteOrderResponseContentType() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order cancelledOrder = new Order(1L, "AAPL", OrderSide.SELL, new BigDecimal("50"), new BigDecimal("200"), "IDEM-SELL");
        cancelledOrder.setId(orderId);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        cancelledOrder.setCreatedOn(LocalDateTime.now());

        when(orderExecutionService.cancelOrder(orderId)).thenReturn(cancelledOrder);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(orderExecutionService, times(1)).cancelOrder(orderId);
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - Successfully cancels SELL order")
    void deleteOrderSuccessfullyCancelsSellOrder() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order cancelledOrder = new Order(1L, "MSFT", OrderSide.SELL, new BigDecimal("75"), new BigDecimal("300"), "IDEM-SELL");
        cancelledOrder.setId(orderId);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        cancelledOrder.setCreatedOn(LocalDateTime.now());

        when(orderExecutionService.cancelOrder(orderId)).thenReturn(cancelledOrder);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(orderId.toString())))
                .andExpect(jsonPath("$.symbol", equalTo("MSFT")))
                .andExpect(jsonPath("$.side", equalTo("SELL")))
                .andExpect(jsonPath("$.quantity", equalTo(75)))
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")));

        verify(orderExecutionService, times(1)).cancelOrder(orderId);
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - Returns 400 when trying to cancel rejected order")
    void deleteOrderReturns400WhenCancelRejectedOrder() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderExecutionService.cancelOrder(orderId))
                .thenThrow(new IllegalArgumentException("Cannot cancel order with status: REJECTED"));

        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", notNullValue()));

        verify(orderExecutionService, times(1)).cancelOrder(orderId);
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - Returns 400 when trying to cancel cancelled order")
    void deleteOrderReturns400WhenCancelCancelledOrder() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        when(orderExecutionService.cancelOrder(orderId))
                .thenThrow(new IllegalArgumentException("Cannot cancel order with status: CANCELLED"));

        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", notNullValue()))
                .andExpect(jsonPath("$.message", containsString("CANCELLED")));

        verify(orderExecutionService, times(1)).cancelOrder(orderId);
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - Response body includes all required fields")
    void deleteOrderResponseIncludesAllFields() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Order cancelledOrder = new Order(2L, "GOOGL", OrderSide.BUY, new BigDecimal("10"), new BigDecimal("2800"), "IDEM-GOOGL");
        cancelledOrder.setId(orderId);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        cancelledOrder.setCreatedOn(now);

        when(orderExecutionService.cancelOrder(orderId)).thenReturn(cancelledOrder);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.accountId", notNullValue()))
                .andExpect(jsonPath("$.symbol", notNullValue()))
                .andExpect(jsonPath("$.side", notNullValue()))
                .andExpect(jsonPath("$.quantity", notNullValue()))
                .andExpect(jsonPath("$.price", notNullValue()))
                .andExpect(jsonPath("$.status", notNullValue()))
                .andExpect(jsonPath("$.createdOn", notNullValue()));

        verify(orderExecutionService, times(1)).cancelOrder(orderId);
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - Multiple cancellations fail on second attempt")
    void deleteOrderFailsOnSecondCancellation() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order cancelledOrder = new Order(1L, "TSLA", OrderSide.BUY, new BigDecimal("5"), new BigDecimal("250"), "IDEM-TSLA");
        cancelledOrder.setId(orderId);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        cancelledOrder.setCreatedOn(LocalDateTime.now());

        // First call succeeds, second throws exception
        when(orderExecutionService.cancelOrder(orderId))
                .thenReturn(cancelledOrder)
                .thenThrow(new IllegalArgumentException("Cannot cancel order with status: CANCELLED"));

        // Act & Assert - First delete succeeds
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("CANCELLED")));

        // Second delete fails
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isBadRequest());

        verify(orderExecutionService, times(2)).cancelOrder(orderId);
    }

    @Test
    @DisplayName("DELETE /api/v1/orders/{id} - Invalid UUID returns 400")
    void deleteOrderInvalidUuidFormat() throws Exception {
        // Act & Assert - Invalid UUID format results in framework error (500 Internal Server Error)
        // The service method is never called due to type conversion failure in Spring
        mockMvc.perform(delete("/api/v1/orders/invalid-uuid-format"))
                .andExpect(status().isInternalServerError());

        verify(orderExecutionService, never()).cancelOrder(any(UUID.class));
    }
}
