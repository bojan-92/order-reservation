package com.mds.inventoryservice.exception;

/**
 * A message that can never be processed successfully (wrong type, schema, or payload). Not retried; dead-lettered.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
