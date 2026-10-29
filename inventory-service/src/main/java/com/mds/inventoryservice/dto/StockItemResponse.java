package com.mds.inventoryservice.dto;

import com.mds.inventoryservice.model.StockItem;

import java.time.Instant;

public record StockItemResponse(String itemId, int available, Instant updatedAt) {

    public static StockItemResponse from(StockItem item) {
        return new StockItemResponse(item.getItemId(), item.getAvailable(), item.getUpdatedAt());
    }
}
