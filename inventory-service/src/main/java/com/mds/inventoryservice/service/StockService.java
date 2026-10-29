package com.mds.inventoryservice.service;

import com.mds.inventoryservice.dto.StockItemResponse;
import com.mds.inventoryservice.exception.NotFoundException;
import com.mds.inventoryservice.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only queries on stock levels.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    /**
     * Lists all stock items.
     *
     * @return stock items, sorted by item id
     */
    @Transactional(readOnly = true)
    public List<StockItemResponse> listStock() {
        return stockRepository.findAll(Sort.by("itemId")).stream().map(StockItemResponse::from).toList();
    }

    /**
     * Looks up the stock level of one item.
     *
     * @param itemId item id
     * @return the stock item
     * @throws NotFoundException if no item has this id
     */
    @Transactional(readOnly = true)
    public StockItemResponse getStockItem(String itemId) {
        return stockRepository.findById(itemId)
                .map(StockItemResponse::from)
                .orElseThrow(() -> new NotFoundException("Item '" + itemId + "' not found"));
    }
}
