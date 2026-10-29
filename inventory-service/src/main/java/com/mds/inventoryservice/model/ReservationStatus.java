package com.mds.inventoryservice.model;

/**
 * Outcome of trying to reserve stock for an order. Both values are final.
 */
public enum ReservationStatus {
    RESERVED,
    REJECTED
}
