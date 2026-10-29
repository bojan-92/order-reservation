package com.mds.orderapi.exception;

/**
 * An incoming event can never be processed (wrong type or schema version, malformed payload, unknown order).
 * It isn't retried; the message goes straight to the dead-letter queue.
 */
public class InvalidEventException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message why the event is invalid
     */
    public InvalidEventException(String message) {
        super(message);
    }

    /**
     * Creates the exception with the underlying cause.
     *
     * @param message why the event is invalid
     * @param cause   underlying parsing error
     */
    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}