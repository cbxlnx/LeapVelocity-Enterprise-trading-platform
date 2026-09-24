package com.leapvelocity.entities.enums.api;

import org.springframework.http.HttpStatus;

/**
 * Enum defining all API error codes with their corresponding HTTP status codes and messages.
 * Used for mapping domain exceptions to standardized API error responses.
 */
public enum ErrorCode {
    // Client errors (4xx)
    ACCOUNT_NOT_FOUND("ERR_001", HttpStatus.NOT_FOUND, "Account not found"),
    ACCOUNT_NOT_ACTIVE("ERR_002", HttpStatus.FORBIDDEN, "Account is not active"),
    INSTRUMENT_NOT_FOUND("ERR_003", HttpStatus.NOT_FOUND, "Instrument/Symbol not found"),
    INSUFFICIENT_FUNDS("ERR_004", HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds for this transaction"),
    INSUFFICIENT_HOLDINGS("ERR_005", HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient holdings to sell"),
    DUPLICATE_ORDER("ERR_006", HttpStatus.CONFLICT, "Order with this idempotency key already exists"),
    INVALID_INPUT("ERR_007", HttpStatus.BAD_REQUEST, "Invalid input parameters"),
    VALIDATION_FAILED("ERR_008", HttpStatus.BAD_REQUEST, "Validation failed"),
    
    // Server errors (5xx)
    INTERNAL_SERVER_ERROR("ERR_999", HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
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
