package com.mds.orderapi.model;

/**
 * Order lifecycle: {@code PENDING} until the inventory service answers, then {@code RESERVED} or
 * {@code REJECTED}. Final states never change.
 */
public enum OrderStatus {
    PENDING,
    RESERVED,
    REJECTED
}
