package com.leapvelocity.exceptions;

/**
 * Exception thrown when an order cannot be found.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String id) {
        super("Order not found: " + id);
    }

    public OrderNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
