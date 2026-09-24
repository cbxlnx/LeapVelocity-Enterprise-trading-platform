package com.leapvelocity.exceptions;

import com.leapvelocity.dto.response.ErrorResponse;
import com.leapvelocity.dto.response.ValidationErrorResponse;
import com.leapvelocity.entities.enums.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Global exception handler for the application.
 * Handles all domain-specific exceptions and maps them to appropriate HTTP responses with error codes.
 * Uses @ControllerAdvice to centralize exception handling across all controllers.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles AccountNotFoundException.
     * Maps to HTTP 404 Not Found with error code ERR_001.
     */
    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleAccountNotFoundException(
            AccountNotFoundException ex,
            HttpServletRequest request) {
        logger.warn("Account not found: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.ACCOUNT_NOT_FOUND,
                request.getRequestURI(),
                ex.getMessage()
        );
        return ResponseEntity.status(ErrorCode.ACCOUNT_NOT_FOUND.getHttpStatus()).body(response);
    }

    /**
     * Handles AccountNotActiveException.
     * Maps to HTTP 403 Forbidden with error code ERR_002.
     */
    @ExceptionHandler(AccountNotActiveException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ErrorResponse> handleAccountNotActiveException(
            AccountNotActiveException ex,
            HttpServletRequest request) {
        logger.warn("Account is not active: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.ACCOUNT_NOT_ACTIVE,
                request.getRequestURI(),
                ex.getMessage()
        );
        return ResponseEntity.status(ErrorCode.ACCOUNT_NOT_ACTIVE.getHttpStatus()).body(response);
    }

    /**
     * Handles InstrumentNotFoundException.
     * Maps to HTTP 404 Not Found with error code ERR_003.
     */
    @ExceptionHandler(InstrumentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFoundException(
            InstrumentNotFoundException ex,
            HttpServletRequest request) {
        logger.warn("Instrument not found: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INSTRUMENT_NOT_FOUND,
                request.getRequestURI(),
                ex.getMessage()
        );
        return ResponseEntity.status(ErrorCode.INSTRUMENT_NOT_FOUND.getHttpStatus()).body(response);
    }

    /**
     * Handles InsufficientFundsException.
     * Maps to HTTP 422 Unprocessable Entity with error code ERR_004.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(
            InsufficientFundsException ex,
            HttpServletRequest request) {
        logger.warn("Insufficient funds: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INSUFFICIENT_FUNDS,
                request.getRequestURI(),
                ex.getMessage()
        );
        return ResponseEntity.status(ErrorCode.INSUFFICIENT_FUNDS.getHttpStatus()).body(response);
    }

    /**
     * Handles InsufficientHoldingsException.
     * Maps to HTTP 422 Unprocessable Entity with error code ERR_005.
     */
    @ExceptionHandler(InsufficientHoldingsException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ResponseEntity<ErrorResponse> handleInsufficientHoldingsException(
            InsufficientHoldingsException ex,
            HttpServletRequest request) {
        logger.warn("Insufficient holdings: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INSUFFICIENT_HOLDINGS,
                request.getRequestURI(),
                ex.getMessage()
        );
        return ResponseEntity.status(ErrorCode.INSUFFICIENT_HOLDINGS.getHttpStatus()).body(response);
    }

    /**
     * Handles DuplicateOrderException.
     * Maps to HTTP 409 Conflict with error code ERR_006.
     */
    @ExceptionHandler(DuplicateOrderException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<ErrorResponse> handleDuplicateOrderException(
            DuplicateOrderException ex,
            HttpServletRequest request) {
        logger.warn("Duplicate order detected: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.DUPLICATE_ORDER,
                request.getRequestURI(),
                ex.getMessage()
        );
        return ResponseEntity.status(ErrorCode.DUPLICATE_ORDER.getHttpStatus()).body(response);
    }

    /**
     * Handles OrderNotFoundException.
     * Maps to HTTP 404 Not Found with error code ERR_003.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ErrorResponse> handleOrderNotFoundException(
            OrderNotFoundException ex,
            HttpServletRequest request) {
        logger.warn("Order not found: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INSTRUMENT_NOT_FOUND,  // Reuse 404 NOT_FOUND error code
                request.getRequestURI(),
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handles MethodArgumentNotValidException for request validation errors.
     * Maps to HTTP 400 Bad Request with error code ERR_008 and field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ValidationErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        logger.warn("Validation failed for request: {}", request.getRequestURI());
        
        ValidationErrorResponse response = new ValidationErrorResponse(request.getRequestURI());
        
        // Extract field errors
        ex.getBindingResult().getFieldErrors().forEach(error ->
                response.addFieldError(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getRejectedValue()
                )
        );
        
        // Extract global errors
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                response.addFieldError(
                        error.getObjectName(),
                        error.getDefaultMessage()
                )
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles IllegalArgumentException for invalid business logic operations.
     * Maps to HTTP 400 Bad Request with error code ERR_007.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        logger.warn("Invalid argument: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INVALID_INPUT,
                request.getRequestURI()
        );
        response.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Generic exception handler for any unexpected RuntimeExceptions.
     * Maps to HTTP 500 Internal Server Error.
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex,
            HttpServletRequest request) {
        logger.error("Unexpected runtime exception occurred at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                request.getRequestURI(),
                "An unexpected error occurred"
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus()).body(response);
    }

    /**
     * Generic exception handler for any unexpected exceptions.
     * Maps to HTTP 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex,
            HttpServletRequest request) {
        logger.error("Unexpected exception occurred at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorResponse response = new ErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                request.getRequestURI(),
                "An unexpected error occurred"
        );
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus()).body(response);
    }
}
