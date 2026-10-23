package com.neueda.trading.engine.exceptions;

/**
 * Indicates a permanent failure that cannot be recovered by retrying.
 * Examples: invalid message format, malformed JSON, missing required fields.
 *
 * Messages that throw this exception are sent directly to the Dead Letter Topic.
 */
public class PermanentFailureException extends Exception {

    public PermanentFailureException(String message) {
        super(message);
    }

    public PermanentFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
