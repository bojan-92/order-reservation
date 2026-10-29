package com.mds.inventoryservice.repository;

import com.mds.inventoryservice.model.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Stock storage, keyed by {@code itemId}.
 */
public interface StockRepository extends JpaRepository<StockItem, String> {

    /**
     * Takes the quantity from stock only if enough is available. Checking and subtracting in one statement
     * means two orders for the same item can't both take the last units.
     *
     * @param itemId   item to reserve
     * @param quantity units to take
     * @return 1 if reserved, 0 if the item doesn't exist or has too little stock
     */
    @Modifying
    @Query(value = """
            UPDATE stock_items
            SET available = available - :quantity, updated_at = now()
            WHERE item_id = :itemId AND available >= :quantity
            """, nativeQuery = true)
    int tryReserve(String itemId, int quantity);
}
