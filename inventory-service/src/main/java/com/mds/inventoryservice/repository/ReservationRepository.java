package com.mds.inventoryservice.repository;

import com.mds.inventoryservice.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, String> {
}
