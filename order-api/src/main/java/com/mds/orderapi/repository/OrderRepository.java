package com.mds.orderapi.repository;

import com.mds.orderapi.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Order storage, keyed by the client-supplied {@code orderId}.
 */
public interface OrderRepository extends JpaRepository<Order, String> {
}
