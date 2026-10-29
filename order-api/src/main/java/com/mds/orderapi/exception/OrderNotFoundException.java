package com.mds.orderapi.exception;

/**
 * No order exists with the requested id.
 */
public class OrderNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param orderId the id that was looked up
     */
    public OrderNotFoundException(String orderId) {
        super("Order with id '" + orderId + "' not found");
    }
}