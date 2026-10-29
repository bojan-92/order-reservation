package com.mds.inventoryservice.exception;

/**
 * No stock item or reservation exists with the requested id.
 */
public class NotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what was looked up and not found
     */
    public NotFoundException(String message) {
        super(message);
    }
}
