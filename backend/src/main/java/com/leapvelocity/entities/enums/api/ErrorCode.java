package com.leapvelocity.entities.enums.api;

import org.springframework.http.HttpStatus;

/**
 * Enum defining all API error codes with their corresponding HTTP status codes and messages.
 * Used for mapping domain exceptions to standardized API error responses.
 */
public enum ErrorCode {
    // Client errors (4xx) - Account errors
    ACCOUNT_NOT_FOUND("ACC-404", HttpStatus.NOT_FOUND, "Account not found"),
    ACCOUNT_NOT_ACTIVE("ACC-403", HttpStatus.FORBIDDEN, "Account not active"),
    
    // Instrument errors
    INSTRUMENT_NOT_FOUND("INS-404", HttpStatus.NOT_FOUND, "Instrument not found"),
    
    // Order errors
    INSUFFICIENT_FUNDS("ORD-400", HttpStatus.BAD_REQUEST, "Insufficient funds"),
    INSUFFICIENT_HOLDINGS("ORD-409", HttpStatus.CONFLICT, "Insufficient holdings"),
    DUPLICATE_ORDER("ORD-409", HttpStatus.CONFLICT, "Duplicate order"),
    
    // Validation errors
    INVALID_INPUT("VAL-422", HttpStatus.UNPROCESSABLE_ENTITY, "Invalid input"),
    VALIDATION_FAILED("VAL-422", HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed"),
    
    // Authentication/Authorization errors
    UNAUTHORIZED("AUTH-401", HttpStatus.UNAUTHORIZED, "Unauthorised / invalid token"),
    FORBIDDEN("AUTH-403", HttpStatus.FORBIDDEN, "Forbidden"),
    
    // Server errors (5xx)
    INTERNAL_SERVER_ERROR("ERR-999", HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
    ;

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public int getHttpStatusCode() {
        return httpStatus.value();
    }

    public String getMessage() {
        return message;
    }
}
