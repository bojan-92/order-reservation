package com.mds.inventoryservice.controller;

import com.mds.inventoryservice.dto.ReservationResponse;
import com.mds.inventoryservice.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping("/{orderId}")
    public ReservationResponse getReservation(@PathVariable String orderId) {
        return reservationService.getReservation(orderId);
    }
}
