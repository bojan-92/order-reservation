package com.mds.inventoryservice.dto;

import com.mds.inventoryservice.model.StockItem;

import java.time.Instant;

/**
 * Stock item as returned by the REST API.
 *
 * @param itemId    item id
 * @param available units available to reserve
 * @param updatedAt when the stock level last changed
 */
public record StockItemResponse(String itemId, int available, Instant updatedAt) {
    public static StockItemResponse from(StockItem item) {
        return new StockItemResponse(item.getItemId(), item.getAvailable(), item.getUpdatedAt());
    }
}
