package com.mds.inventoryservice.repository;

import com.mds.inventoryservice.model.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StockRepository extends JpaRepository<StockItem, String> {

    /**
     * Check-and-decrement in a single statement: the row lock taken by UPDATE serialises concurrent
     * reservations for the same item, so stock can never go negative or be oversold.
     *
     * @return 1 if the stock was reserved, 0 if the item is unknown or has too little stock
     */
    @Modifying
    @Query(value = """
            UPDATE stock_items
            SET available = available - :quantity, updated_at = now()
            WHERE item_id = :itemId AND available >= :quantity
            """, nativeQuery = true)
    int tryReserve(String itemId, int quantity);
}
