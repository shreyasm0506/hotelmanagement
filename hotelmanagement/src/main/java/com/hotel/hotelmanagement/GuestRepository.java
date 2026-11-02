package com.hotel.hotelmanagement;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, Long> {

    Optional<Guest> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
