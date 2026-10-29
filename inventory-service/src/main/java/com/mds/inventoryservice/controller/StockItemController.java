package com.mds.inventoryservice.controller;

import com.mds.inventoryservice.dto.StockItemResponse;
import com.mds.inventoryservice.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Stock")
@RestController
@RequestMapping("/stock-items")
@RequiredArgsConstructor
public class StockItemController {

    private final StockService stockService;

    @Operation(summary = "List stock levels")
    @ApiResponse(responseCode = "200", description = "All stock items")
    @GetMapping
    public List<StockItemResponse> listStock() {
        return stockService.listStock();
    }

    @Operation(summary = "Get one item's stock level")
    @ApiResponse(responseCode = "200", description = "Stock item found")
    @ApiResponse(responseCode = "404", description = "Unknown item",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{itemId}")
    public StockItemResponse getStockItem(@Parameter(example = "item-1") @PathVariable String itemId) {
        return stockService.getStockItem(itemId);
    }
}
