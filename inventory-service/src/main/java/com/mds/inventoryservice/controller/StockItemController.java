package com.mds.inventoryservice.controller;

import com.mds.inventoryservice.dto.StockItemResponse;
import com.mds.inventoryservice.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-only view of stock levels. Stock changes only through OrderPlaced events.
 */
@RestController
@RequestMapping("/stock-items")
@RequiredArgsConstructor
public class StockItemController {

    private final StockService stockService;

    @GetMapping
    public List<StockItemResponse> listStock() {
        return stockService.listStock();
    }

    @GetMapping("/{itemId}")
    public StockItemResponse getStockItem(@PathVariable String itemId) {
        return stockService.getStockItem(itemId);
    }
}
