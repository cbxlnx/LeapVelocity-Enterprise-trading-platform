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
}
