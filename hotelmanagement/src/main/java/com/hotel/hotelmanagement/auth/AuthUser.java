package com.hotel.hotelmanagement.auth;

// The logged-in user, taken from a verified token on every request.
public record AuthUser(Long guestId, String role) {

    public static final String GUEST = "GUEST";
    public static final String ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }
}
