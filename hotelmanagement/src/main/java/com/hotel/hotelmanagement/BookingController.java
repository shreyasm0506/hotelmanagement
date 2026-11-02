package com.hotel.hotelmanagement;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private static final int MIN_GUESTS = 1;
    private static final int MAX_GUESTS = 10;

    private static final String CONFIRMED = "CONFIRMED";
    private static final String CANCELLED = "CANCELLED";
    private static final String COMPLETED = "COMPLETED";

    // Request body for booking "a Deluxe room" instead of a specific room.
    public record BookingByTypeRequest(
            Long guestId,
            String roomType,
            String checkInDate,
            String checkOutDate,
            Integer guestCount) {
    }

    private final BookingRepository bookingRepository;
    private final GuestRepository guestRepository;
    private final RoomRepository roomRepository;

    public BookingController(
            BookingRepository bookingRepository,
            GuestRepository guestRepository,
            RoomRepository roomRepository) {

        this.bookingRepository = bookingRepository;
        this.guestRepository = guestRepository;
        this.roomRepository = roomRepository;
    }

    // ---------- Read ----------

    @GetMapping
    public List<Booking> getAllBookings() {
        return bookingRepository.findAll();
    }

    @GetMapping("/{id}")
    public Booking getBookingById(@PathVariable Long id) {
        return findBookingOr404(id);
    }

    // Returns JSON: { "roomId": 1, "available": true, "message": "...", "reason": "..." }
    @GetMapping("/availability")
    public Map<String, Object> checkRoomAvailability(
            @RequestParam Long roomId,
            @RequestParam String checkInDate,
            @RequestParam String checkOutDate) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Room not found"));

        LocalDate checkIn = parseDate(checkInDate, "Check-in date");
        LocalDate checkOut = parseDate(checkOutDate, "Check-out date");
        validateDateOrder(checkIn, checkOut);

        boolean booked = bookingRepository.countOverlapping(
                roomId, checkIn.toString(), checkOut.toString()) > 0;

        boolean available = room.isAvailable() && !booked;

        String reason;
        if (!room.isAvailable()) {
            reason = "Room is currently unavailable";
        } else if (booked) {
            reason = "Room is already booked for these dates";
        } else {
            reason = "Room is free for these dates";
        }

        // "message" keeps the original wording so any client that still
        // checks the response text for "Room is AVAILABLE" keeps working.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roomId", roomId);
        result.put("available", available);
        result.put("message", available ? "Room is AVAILABLE" : "Room is NOT available");
        result.put("reason", reason);
        return result;
    }

    // ---------- Create ----------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Booking addBooking(@RequestBody Booking request) {

        if (request.getGuestId() == null) {
            throw new IllegalArgumentException("Guest is required");
        }

        if (request.getRoomId() == null) {
            throw new IllegalArgumentException("Room is required");
        }

        Guest guest = guestRepository.findById(request.getGuestId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Guest not found"));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Room not found"));

        LocalDate checkIn = parseDate(request.getCheckInDate(), "Check-in date");
        LocalDate checkOut = parseDate(request.getCheckOutDate(), "Check-out date");
        validateDateOrder(checkIn, checkOut);

        if (checkIn.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Check-in date cannot be in the past");
        }

        int guestCount = request.getGuestCount() == null
                ? MIN_GUESTS
                : validateGuestCount(request.getGuestCount());

        String status = request.getStatus() == null || request.getStatus().isBlank()
                ? CONFIRMED
                : normalizeStatus(request.getStatus());

        if (CONFIRMED.equals(status)) {
            if (!room.isAvailable()) {
                throw new IllegalArgumentException(
                        "Room is currently unavailable");
            }

            if (bookingRepository.countOverlapping(
                    room.getId(), checkIn.toString(), checkOut.toString()) > 0) {
                throw new IllegalArgumentException(
                        "Room is already booked for these dates");
            }
        }

        // Build a fresh entity so a client-supplied id can never
        // overwrite an existing booking.
        Booking booking = new Booking();
        booking.setGuest(guest);
        booking.setRoom(room);
        booking.setCheckInDate(checkIn.toString());
        booking.setCheckOutDate(checkOut.toString());
        booking.setGuestCount(guestCount);
        booking.setStatus(status);

        return bookingRepository.save(booking);
    }

    // Books the first free room of the requested type (cheapest first).
    @PostMapping("/by-type")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Booking addBookingByType(@RequestBody BookingByTypeRequest request) {

        if (request.roomType() == null || request.roomType().isBlank()) {
            throw new IllegalArgumentException("Room type is required");
        }

        LocalDate checkIn = parseDate(request.checkInDate(), "Check-in date");
        LocalDate checkOut = parseDate(request.checkOutDate(), "Check-out date");
        validateDateOrder(checkIn, checkOut);

        String wantedType = request.roomType().trim();

        Room room = roomRepository.findAll().stream()
                .filter(r -> r.isAvailable())
                .filter(r -> r.getRoomType() != null
                        && r.getRoomType().trim().equalsIgnoreCase(wantedType))
                .sorted(Comparator.comparingDouble(Room::getPricePerNight)
                        .thenComparing(Room::getId))
                .filter(r -> bookingRepository.countOverlapping(
                        r.getId(), checkIn.toString(), checkOut.toString()) == 0)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No " + wantedType + " rooms are available for these dates"));

        // Reuse the normal create logic (guest check, past-date check, etc.)
        Booking booking = new Booking();
        booking.setGuestId(request.guestId());
        booking.setRoomId(room.getId());
        booking.setCheckInDate(checkIn.toString());
        booking.setCheckOutDate(checkOut.toString());
        booking.setGuestCount(request.guestCount());
        booking.setStatus(CONFIRMED);

        return addBooking(booking);
    }

    // ---------- Update ----------

    @PutMapping("/{id}")
    @Transactional
    public Booking updateBooking(
            @PathVariable Long id,
            @RequestBody Booking request) {

        Booking booking = findBookingOr404(id);

        if (request.getGuestId() == null) {
            throw new IllegalArgumentException("Guest is required");
        }

        if (request.getRoomId() == null) {
            throw new IllegalArgumentException("Room is required");
        }

        Guest guest = guestRepository.findById(request.getGuestId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Guest not found"));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Room not found"));

        LocalDate checkIn = parseDate(request.getCheckInDate(), "Check-in date");
        LocalDate checkOut = parseDate(request.getCheckOutDate(), "Check-out date");
        validateDateOrder(checkIn, checkOut);

        if (request.getStatus() == null || request.getStatus().isBlank()) {
            throw new IllegalArgumentException(
                    "Status is required. Use CONFIRMED, CANCELLED, or COMPLETED");
        }

        String status = normalizeStatus(request.getStatus());

        // Keep the existing guest count unless a new one was sent.
        Integer guestCount = request.getGuestCount() == null
                ? booking.getGuestCount()
                : Integer.valueOf(validateGuestCount(request.getGuestCount()));

        if (CONFIRMED.equals(status)) {
            boolean roomChanged = !room.getId().equals(booking.getRoomId());

            if (roomChanged && !room.isAvailable()) {
                throw new IllegalArgumentException(
                        "Room is currently unavailable");
            }

            if (bookingRepository.countOverlappingExcluding(
                    room.getId(), checkIn.toString(),
                    checkOut.toString(), id) > 0) {
                throw new IllegalArgumentException(
                        "Room is already booked for these dates");
            }
        }

        booking.setGuest(guest);
        booking.setRoom(room);
        booking.setCheckInDate(checkIn.toString());
        booking.setCheckOutDate(checkOut.toString());
        booking.setGuestCount(guestCount);
        booking.setStatus(status);

        return bookingRepository.save(booking);
    }

    @PutMapping("/{id}/cancel")
    public Booking cancelBooking(@PathVariable Long id) {
        Booking booking = findBookingOr404(id);

        if (CANCELLED.equalsIgnoreCase(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Booking is already cancelled");
        }

        booking.setStatus(CANCELLED);
        return bookingRepository.save(booking);
    }

    // ---------- Delete ----------

    @DeleteMapping("/{id}")
    public String deleteBooking(@PathVariable Long id) {
        if (!bookingRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Booking not found");
        }

        bookingRepository.deleteById(id);
        return "Booking deleted successfully";
    }

    // ---------- Helpers ----------

    private Booking findBookingOr404(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }

        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    fieldName + " must be in YYYY-MM-DD format");
        }
    }

    private void validateDateOrder(LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                    "Check-out date must be after check-in date");
        }
    }

    private int validateGuestCount(Integer guestCount) {
        if (guestCount < MIN_GUESTS || guestCount > MAX_GUESTS) {
            throw new IllegalArgumentException(
                    "Guest count must be between " + MIN_GUESTS +
                    " and " + MAX_GUESTS);
        }

        return guestCount;
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase();

        if (!normalized.equals(CONFIRMED)
                && !normalized.equals(CANCELLED)
                && !normalized.equals(COMPLETED)) {
            throw new IllegalArgumentException(
                    "Invalid status. Use CONFIRMED, CANCELLED, or COMPLETED");
        }

        return normalized;
    }

    // ---------- Error handling ----------

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_PLAIN)
                .body(ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(
            ResponseStatusException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .contentType(MediaType.TEXT_PLAIN)
                .body(ex.getReason() != null ? ex.getReason() : "Request failed");
    }
}