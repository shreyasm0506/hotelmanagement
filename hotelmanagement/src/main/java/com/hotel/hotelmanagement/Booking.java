package com.hotel.hotelmanagement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // Stored as ISO strings (YYYY-MM-DD). ISO strings sort the same way
    // dates do, which the overlap query in BookingRepository relies on.
    @Column(nullable = false)
    private String checkInDate;

    @Column(nullable = false)
    private String checkOutDate;

    // Nullable so bookings created before this column existed still load.
    private Integer guestCount;

    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGuestId() {
        return guest != null ? guest.getId() : null;
    }

    public void setGuestId(Long guestId) {
        if (guestId == null) {
            this.guest = null;
        } else {
            this.guest = new Guest();
            this.guest.setId(guestId);
        }
    }

    public Long getRoomId() {
        return room != null ? room.getId() : null;
    }

    public void setRoomId(Long roomId) {
        if (roomId == null) {
            this.room = null;
        } else {
            this.room = new Room();
            this.room.setId(roomId);
        }
    }

    public String getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(String checkInDate) {
        this.checkInDate = checkInDate;
    }

    public String getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(String checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public Integer getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(Integer guestCount) {
        this.guestCount = guestCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setGuest(Guest guest) {
        this.guest = guest;
    }

    public void setRoom(Room room) {
        this.room = room;
    }
}
