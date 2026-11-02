package com.hotel.hotelmanagement;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Admin only (see SecurityConfig). Guests sign up through /api/auth/register.
@RestController
@RequestMapping("/api/guests")
public class GuestController {

    // Create or update a guest record. Passwords and roles are not set here.
    public record GuestRequest(String fullName, String phoneNumber, String email) {
    }

    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;

    public GuestController(
            GuestRepository guestRepository,
            BookingRepository bookingRepository) {
        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping
    public List<GuestResponse> getAllGuests() {
        return guestRepository.findAll().stream()
                .map(GuestResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public GuestResponse getGuestById(@PathVariable Long id) {
        return GuestResponse.from(findGuestOr404(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuestResponse addGuest(@RequestBody GuestRequest request) {
        Guest guest = new Guest();
        applyRequest(guest, request);
        return GuestResponse.from(guestRepository.save(guest));
    }

    @PutMapping("/{id}")
    public GuestResponse updateGuest(
            @PathVariable Long id,
            @RequestBody GuestRequest request) {

        Guest guest = findGuestOr404(id);
        applyRequest(guest, request);
        return GuestResponse.from(guestRepository.save(guest));
    }

    @DeleteMapping("/{id}")
    public String deleteGuest(@PathVariable Long id) {

        if (!guestRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Guest not found");
        }

        if (bookingRepository.existsByGuest_Id(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete guest because bookings exist");
        }

        guestRepository.deleteById(id);
        return "Guest deleted successfully";
    }

    private Guest findGuestOr404(Long id) {
        return guestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Guest not found"));
    }

    private void applyRequest(Guest guest, GuestRequest request) {
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Full name is required");
        }

        guest.setFullName(request.fullName().trim());

        String phone = request.phoneNumber() == null
                ? "" : request.phoneNumber().trim();
        guest.setPhoneNumber(phone.isEmpty() ? null : phone);

        // Blank email is stored as null so several guests can have none
        String email = request.email() == null
                ? "" : request.email().trim().toLowerCase();
        guest.setEmail(email.isEmpty() ? null : email);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(
            ResponseStatusException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .contentType(MediaType.TEXT_PLAIN)
                .body(ex.getReason() != null ? ex.getReason() : "Request failed");
    }

    // The only unique column on Guest is email.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDuplicateGuest(
            DataIntegrityViolationException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.TEXT_PLAIN)
                .body("A guest with this email already exists");
    }
}
