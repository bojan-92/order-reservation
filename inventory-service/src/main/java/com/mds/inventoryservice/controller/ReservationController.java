package com.mds.inventoryservice.controller;

import com.mds.inventoryservice.dto.ReservationResponse;
import com.mds.inventoryservice.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Reservations")
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "Get the reservation result for an order",
            description = "`RESERVED`, or `REJECTED` with a reason such as `INSUFFICIENT_STOCK` or `UNKNOWN_ITEM`.")
    @ApiResponse(responseCode = "200", description = "Order has been processed")
    @ApiResponse(responseCode = "404", description = "Order not processed (yet)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{orderId}")
    public ReservationResponse getReservation(@Parameter(example = "o-1") @PathVariable String orderId) {
        return reservationService.getReservation(orderId);
    }
}
