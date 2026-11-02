package com.hotel.hotelmanagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByGuest_Id(Long guestId);

    boolean existsByRoom_Id(Long roomId);

    // Counts CONFIRMED bookings for a room that overlap [checkIn, checkOut).
    // Dates are ISO strings (YYYY-MM-DD), which compare correctly as text.
    @Query("""
            select count(b) from Booking b
            where b.room.id = :roomId
              and upper(b.status) = 'CONFIRMED'
              and b.checkInDate < :checkOut
              and b.checkOutDate > :checkIn
            """)
    long countOverlapping(
            @Param("roomId") Long roomId,
            @Param("checkIn") String checkIn,
            @Param("checkOut") String checkOut);

    // Same as above, ignoring one booking (used when editing that booking).
    @Query("""
            select count(b) from Booking b
            where b.room.id = :roomId
              and b.id <> :excludeId
              and upper(b.status) = 'CONFIRMED'
              and b.checkInDate < :checkOut
              and b.checkOutDate > :checkIn
            """)
    long countOverlappingExcluding(
            @Param("roomId") Long roomId,
            @Param("checkIn") String checkIn,
            @Param("checkOut") String checkOut,
            @Param("excludeId") Long excludeId);
}