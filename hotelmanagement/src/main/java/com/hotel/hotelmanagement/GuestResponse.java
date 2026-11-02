package com.hotel.hotelmanagement;

// What the API shows about a guest. Never includes the password hash.
public record GuestResponse(
        Long id,
        String fullName,
        String phoneNumber,
        String email,
        String role) {

    public static GuestResponse from(Guest guest) {
        return new GuestResponse(
                guest.getId(),
                guest.getFullName(),
                guest.getPhoneNumber(),
                guest.getEmail(),
                guest.getRole());
    }
}
