package com.mds.orderapi.controller;

import com.mds.orderapi.dto.CreateOrderRequest;
import com.mds.orderapi.dto.OrderResponse;
import com.mds.orderapi.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Orders")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Place an order",
            description = "Stores the order and queues an `OrderPlaced` event for the inventory service. "
                    + "Stock is reserved asynchronously, so the order starts as `PENDING`. "
                    + "Sending an existing `orderId` again returns the stored order unchanged and publishes nothing.")
    @ApiResponse(responseCode = "202", description = "Accepted (new order) or already stored (repeated `orderId`)")
    @ApiResponse(responseCode = "400", description = "Invalid payload",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Same `orderId` is being created by a concurrent request; retry",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.accepted().body(orderService.placeOrder(request));
    }

    @Operation(summary = "Get an order",
            description = "Status is `PENDING` until the inventory service answers, then `RESERVED` or `REJECTED`.")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "No order with this id",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@Parameter(example = "o-1") @PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrder(orderId));
    }
}
