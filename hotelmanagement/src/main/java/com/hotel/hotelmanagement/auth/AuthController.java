package com.hotel.hotelmanagement.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.hotel.hotelmanagement.Guest;
import com.hotel.hotelmanagement.GuestRepository;
import com.hotel.hotelmanagement.GuestResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(
            String fullName,
            String email,
            String phoneNumber,
            String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record AuthResponse(String token, GuestResponse guest) {
    }

    private record Attempts(int count, long windowStart) {
    }

    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE =
            Pattern.compile("^[0-9+()\\- ]{6,20}$");

    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD_BYTES = 72; // BCrypt limit

    // Login throttling: 5 failures per email within 15 minutes
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = 15L * 60 * 1000;
    private static final int MAX_TRACKED = 10_000;

    private final GuestRepository guestRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    private final Map<String, Attempts> failures = new ConcurrentHashMap<>();

    // Compared against when the email is unknown, so "no such account" takes
    // about as long as "wrong password".
    private final String dummyHash;

    public AuthController(
            GuestRepository guestRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {

        this.guestRepository = guestRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-password");
    }

    // ---------- Register ----------

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody RegisterRequest request) {

        String fullName = clean(request.fullName());

        if (fullName.isEmpty()) {
            throw badRequest("Full name is required");
        }

        if (fullName.length() > 100) {
            throw badRequest("Full name must be 100 characters or fewer");
        }

        String email = normalizeEmail(request.email());

        String phone = clean(request.phoneNumber());

        if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
            throw badRequest("Phone number can only contain digits, spaces, + ( ) and -");
        }

        validatePassword(request.password());

        if (guestRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An account with this email already exists");
        }

        Guest guest = new Guest();
        guest.setFullName(fullName);
        guest.setEmail(email);
        guest.setPhoneNumber(phone.isEmpty() ? null : phone);
        guest.setPasswordHash(passwordEncoder.encode(request.password()));
        guest.setRole(AuthUser.GUEST); // never taken from the request

        guest = guestRepository.save(guest);

        return new AuthResponse(
                tokenService.createToken(guest.getId(), guest.getRole()),
                GuestResponse.from(guest));
    }

    // ---------- Login ----------

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {

        String email = clean(request.email()).toLowerCase();
        String password = request.password() == null ? "" : request.password();

        if (email.isEmpty() || password.isEmpty()) {
            throw badRequest("Enter your email and password");
        }

        if (isLocked(email)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Try again in a few minutes.");
        }

        Guest guest = guestRepository.findByEmailIgnoreCase(email).orElse(null);

        boolean valid;

        if (guest == null || guest.getPasswordHash() == null) {
            passwordEncoder.matches(password, dummyHash);
            valid = false;
        } else {
            valid = passwordEncoder.matches(password, guest.getPasswordHash());
        }

        if (!valid) {
            recordFailure(email);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        failures.remove(email);

        return new AuthResponse(
                tokenService.createToken(guest.getId(), guest.getRole()),
                GuestResponse.from(guest));
    }

    // ---------- Current user ----------

    @GetMapping("/me")
    public GuestResponse me(@AuthenticationPrincipal AuthUser user) {

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Please log in");
        }

        Guest guest = guestRepository.findById(user.guestId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Please log in"));

        return GuestResponse.from(guest);
    }

    // ---------- Helpers ----------

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String value) {
        String email = clean(value).toLowerCase();

        if (email.isEmpty()) {
            throw badRequest("Email is required");
        }

        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw badRequest("Enter a valid email address");
        }

        return email;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw badRequest("Password must be at least " + MIN_PASSWORD + " characters");
        }

        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MAX_PASSWORD_BYTES) {
            throw badRequest("Password is too long (72 bytes maximum)");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private boolean isLocked(String key) {
        Attempts attempts = failures.get(key);

        if (attempts == null) {
            return false;
        }

        if (System.currentTimeMillis() - attempts.windowStart() > WINDOW_MILLIS) {
            failures.remove(key);
            return false;
        }

        return attempts.count() >= MAX_FAILURES;
    }

    private void recordFailure(String key) {
        if (failures.size() > MAX_TRACKED) {
            failures.clear();
        }

        long now = System.currentTimeMillis();

        failures.merge(key, new Attempts(1, now), (old, ignored) ->
                now - old.windowStart() > WINDOW_MILLIS
                        ? new Attempts(1, now)
                        : new Attempts(old.count() + 1, old.windowStart()));
    }

    // ---------- Error handling ----------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(
            ResponseStatusException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .contentType(MediaType.TEXT_PLAIN)
                .body(ex.getReason() != null ? ex.getReason() : "Request failed");
    }

    // Two people registering the same email at the same moment
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDuplicate(DataIntegrityViolationException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.TEXT_PLAIN)
                .body("An account with this email already exists");
    }
}
