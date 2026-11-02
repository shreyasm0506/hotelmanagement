package com.hotel.hotelmanagement;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    // One entry per room type, shown as one card on the website.
    public record RoomTypeSummary(
            String roomType,
            double startingPrice,
            int totalRooms,
            int availableRooms) {
    }

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public RoomController(
            RoomRepository roomRepository,
            BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    // Rooms grouped by type. With both dates, availableRooms only counts
    // rooms that are free for those dates; without dates it counts every
    // room that is marked available.
    @GetMapping("/types")
    public List<RoomTypeSummary> getRoomTypes(
            @RequestParam(required = false) String checkInDate,
            @RequestParam(required = false) String checkOutDate) {

        boolean hasDates = checkInDate != null && !checkInDate.isBlank()
                && checkOutDate != null && !checkOutDate.isBlank();

        if ((checkInDate == null || checkInDate.isBlank())
                != (checkOutDate == null || checkOutDate.isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide both check-in and check-out dates");
        }

        String checkIn = null;
        String checkOut = null;

        if (hasDates) {
            try {
                LocalDate in = LocalDate.parse(checkInDate.trim());
                LocalDate out = LocalDate.parse(checkOutDate.trim());

                if (!out.isAfter(in)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Check-out date must be after check-in date");
                }

                checkIn = in.toString();
                checkOut = out.toString();
            } catch (DateTimeParseException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Dates must be in YYYY-MM-DD format");
            }
        }

        Map<String, List<Room>> byType = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (Room room : roomRepository.findAll()) {
            String type = room.getRoomType() == null || room.getRoomType().isBlank()
                    ? "Standard"
                    : room.getRoomType().trim();
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(room);
        }

        List<RoomTypeSummary> result = new ArrayList<>();

        for (Map.Entry<String, List<Room>> entry : byType.entrySet()) {
            List<Room> rooms = entry.getValue();
            int available = 0;
            double lowestPrice = Double.MAX_VALUE;

            for (Room room : rooms) {
                lowestPrice = Math.min(lowestPrice, room.getPricePerNight());

                if (!room.isAvailable()) {
                    continue;
                }

                if (hasDates && bookingRepository.countOverlapping(
                        room.getId(), checkIn, checkOut) > 0) {
                    continue;
                }

                available++;
            }

            result.add(new RoomTypeSummary(
                    entry.getKey(), lowestPrice, rooms.size(), available));
        }

        return result;
    }

    @GetMapping("/{id}")
    public Room getRoomById(@PathVariable Long id) {
        return findRoomOr404(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Room addRoom(@RequestBody Room room) {
        validateRoom(room);

        // Build a fresh entity so a client-supplied id can't overwrite a room.
        Room newRoom = new Room();
        newRoom.setRoomNumber(room.getRoomNumber().trim());
        newRoom.setRoomType(room.getRoomType());
        newRoom.setPricePerNight(room.getPricePerNight());
        newRoom.setAvailable(room.isAvailable());

        return roomRepository.save(newRoom);
    }

    @PutMapping("/{id}")
    public Room updateRoom(@PathVariable Long id, @RequestBody Room updatedRoom) {
        Room room = findRoomOr404(id);

        validateRoom(updatedRoom);

        room.setRoomNumber(updatedRoom.getRoomNumber().trim());
        room.setRoomType(updatedRoom.getRoomType());
        room.setPricePerNight(updatedRoom.getPricePerNight());
        room.setAvailable(updatedRoom.isAvailable());

        return roomRepository.save(room);
    }

    @DeleteMapping("/{id}")
    public String deleteRoom(@PathVariable Long id) {

        if (!roomRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Room not found");
        }

        if (bookingRepository.existsByRoom_Id(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete room because bookings exist");
        }

        roomRepository.deleteById(id);
        return "Room deleted successfully";
    }

    private Room findRoomOr404(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Room not found"));
    }

    private void validateRoom(Room room) {
        if (room.getRoomNumber() == null || room.getRoomNumber().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Room number is required");
        }

        double price = room.getPricePerNight();

        if (Double.isNaN(price) || Double.isInfinite(price) || price < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Price per night must be zero or more");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(
            ResponseStatusException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .contentType(MediaType.TEXT_PLAIN)
                .body(ex.getReason() != null ? ex.getReason() : "Request failed");
    }

    // The only unique column on Room is roomNumber.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDuplicateRoom(
            DataIntegrityViolationException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.TEXT_PLAIN)
                .body("A room with this room number already exists");
    }
}
