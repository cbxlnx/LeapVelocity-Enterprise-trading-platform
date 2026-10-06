package com.neueda.trading.engine.exceptions;

/**
 * Indicates a temporary failure that can potentially be recovered by retrying.
 * Examples: database unavailable, external service timeout, temporary resource exhaustion.
 *
 * Messages that throw this exception are retried with exponential backoff.
 * After the retry budget is exhausted, the message is sent to the Dead Letter Topic.
 */
public class TemporaryFailureException extends Exception {

    public TemporaryFailureException(String message) {
        super(message);
    }

    public TemporaryFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
