package com.leapvelocity.exceptions;

import com.leapvelocity.dto.response.ErrorResponse;
import com.leapvelocity.entities.enums.api.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/accounts/99999");
    }

    @Test
    void testHandleAccountNotFoundException() {
        AccountNotFoundException ex = new AccountNotFoundException("Account ID 99999 not found");
        
        ResponseEntity<ErrorResponse> response = handler.handleAccountNotFoundException(ex, request);
        
        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND.getCode(), response.getBody().getCode());
        assertEquals("Account ID 99999 not found", response.getBody().getDetails());
    }

    @Test
    void testHandleInsufficientFundsException() {
        InsufficientFundsException ex = new InsufficientFundsException(1L, new BigDecimal("500.00"), new BigDecimal("100.00"));
        
        ResponseEntity<ErrorResponse> response = handler.handleInsufficientFundsException(ex, request);
        
        assertEquals(422, response.getStatusCode().value());
        assertEquals(ErrorCode.INSUFFICIENT_FUNDS.getCode(), response.getBody().getCode());
    }

    @Test
    void testHandleDuplicateOrderException() {
        DuplicateOrderException ex = new DuplicateOrderException("Duplicate order with key ABC-123");
        
        ResponseEntity<ErrorResponse> response = handler.handleDuplicateOrderException(ex, request);
        
        assertEquals(409, response.getStatusCode().value());
        assertEquals(ErrorCode.DUPLICATE_ORDER.getCode(), response.getBody().getCode());
    }

    @Test
    void testHandleRuntimeException() {
        RuntimeException ex = new RuntimeException("Unexpected error");
        
        ResponseEntity<ErrorResponse> response = handler.handleRuntimeException(ex, request);
        
        assertEquals(500, response.getStatusCode().value());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), response.getBody().getCode());
    }

    @Test
    void testHandleInsufficientHoldingsException() {
        // Verifies: InsufficientHoldingsException maps to 422 UNPROCESSABLE_ENTITY with ERR_005
        InsufficientHoldingsException ex = new InsufficientHoldingsException(
                1L, "AAPL", new BigDecimal("100"), new BigDecimal("50"));
        
        ResponseEntity<ErrorResponse> response = handler.handleInsufficientHoldingsException(ex, request);
        
        assertEquals(422, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INSUFFICIENT_HOLDINGS.getCode(), response.getBody().getCode());
    }

    @Test
    void testHandleOrderNotFoundException() {
        // Verifies: OrderNotFoundException maps to 404 NOT_FOUND with ERR_003
        OrderNotFoundException ex = new OrderNotFoundException("order-uuid-12345");
        
        ResponseEntity<ErrorResponse> response = handler.handleOrderNotFoundException(ex, request);
        
        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INSTRUMENT_NOT_FOUND.getCode(), response.getBody().getCode());
    }

    @Test
    void testHandleIllegalArgumentException() {
        // Verifies: IllegalArgumentException maps to 400 BAD_REQUEST with ERR_007
        IllegalArgumentException ex = new IllegalArgumentException("Quantity must be positive");
        
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex, request);
        
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INVALID_INPUT.getCode(), response.getBody().getCode());
        assertEquals("Quantity must be positive", response.getBody().getMessage());
    }

    @Test
    void testHandleAccountNotActiveException() {
        // Verifies: AccountNotActiveException maps to 403 FORBIDDEN with ERR_002
        AccountNotActiveException ex = new AccountNotActiveException(1L);
        
        ResponseEntity<ErrorResponse> response = handler.handleAccountNotActiveException(ex, request);
        
        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.ACCOUNT_NOT_ACTIVE.getCode(), response.getBody().getCode());
    }
}
