package com.hotel.hotelmanagement.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates and verifies signed login tokens.
 *
 * Token format:  base64url(payload) + "." + base64url(HMAC-SHA256(payload))
 * Payload:       v1|guestId|role|expiresAtEpochSeconds
 *
 * Only the server knows the secret, so a token cannot be edited or forged.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private static final String HMAC = "HmacSHA256";
    private static final String VERSION = "v1";
    private static final int MIN_SECRET_LENGTH = 32;

    private static final Base64.Encoder ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long ttlSeconds;

    public TokenService(
            @Value("${app.auth.secret:}") String configuredSecret,
            @Value("${app.auth.token-ttl-hours:8}") long ttlHours) {

        if (configuredSecret == null || configuredSecret.isBlank()) {
            // No secret configured: use a random one. Fine for development,
            // but everyone is logged out whenever the backend restarts.
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;

            log.warn("AUTH_SECRET is not set. Using a temporary random secret; "
                    + "logins will not survive a restart. Set AUTH_SECRET "
                    + "(32+ characters) to keep users logged in.");
        } else {
            if (configuredSecret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "app.auth.secret / AUTH_SECRET must be at least "
                        + MIN_SECRET_LENGTH + " characters long");
            }

            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }

        this.ttlSeconds = Math.max(1, ttlHours) * 3600;
    }

    public String createToken(Long guestId, String role) {
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;

        String payload = VERSION + "|" + guestId + "|" + role + "|" + expiresAt;
        String encodedPayload = ENCODER.encodeToString(
                payload.getBytes(StandardCharsets.UTF_8));

        return encodedPayload + "." + ENCODER.encodeToString(sign(encodedPayload));
    }

    public Optional<AuthUser> verify(String token) {
        if (token == null || token.length() > 512) {
            return Optional.empty();
        }

        int dot = token.indexOf('.');

        if (dot <= 0 || dot != token.lastIndexOf('.') || dot == token.length() - 1) {
            return Optional.empty();
        }

        String encodedPayload = token.substring(0, dot);

        try {
            byte[] provided = DECODER.decode(token.substring(dot + 1));

            // Constant-time comparison
            if (!MessageDigest.isEqual(sign(encodedPayload), provided)) {
                return Optional.empty();
            }

            String payload = new String(
                    DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\|", -1);

            if (parts.length != 4 || !VERSION.equals(parts[0])) {
                return Optional.empty();
            }

            long expiresAt = Long.parseLong(parts[3]);

            if (expiresAt <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }

            String role = parts[2];

            if (!AuthUser.GUEST.equals(role) && !AuthUser.ADMIN.equals(role)) {
                return Optional.empty();
            }

            return Optional.of(new AuthUser(Long.valueOf(parts[1]), role));
        } catch (IllegalArgumentException ex) {
            // Bad base64 or a non-numeric field
            return Optional.empty();
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign token", ex);
        }
    }
}
