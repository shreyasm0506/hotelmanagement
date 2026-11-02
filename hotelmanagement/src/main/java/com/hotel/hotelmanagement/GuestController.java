package com.hotel.hotelmanagement;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
@RestController
@RequestMapping("/api/guests")
public class GuestController {

    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;

    public GuestController(
            GuestRepository guestRepository,
            BookingRepository bookingRepository) {
        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
    }
    @GetMapping
    public List<Guest> getAllGuests() {
        return guestRepository.findAll();
    }

    @PostMapping
    public Guest addGuest(@RequestBody Guest guest) {
        return guestRepository.save(guest);
    }
    @GetMapping("/{id}")
    public Guest getGuestById(@PathVariable Long id) {
        return guestRepository.findById(id).orElse(null);
    }
    @PutMapping("/{id}")
    public Guest updateGuest(@PathVariable Long id,
                             @RequestBody Guest updatedGuest) {
        return guestRepository.findById(id).map(guest -> {
            guest.setFullName(updatedGuest.getFullName());
            guest.setPhoneNumber(updatedGuest.getPhoneNumber());
            guest.setEmail(updatedGuest.getEmail());

            return guestRepository.save(guest);
        }).orElse(null);
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
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(
            ResponseStatusException ex) {

        return ResponseEntity
                .status(ex.getStatusCode())
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .body(ex.getReason() != null ? ex.getReason() : "Request failed");
    }
}