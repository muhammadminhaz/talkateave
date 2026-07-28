package com.muhammadminhaz.talkateeve.util;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTests {

    // Base64 of a 41-byte secret; HS256 needs at least 32 bytes.
    private static final String SECRET =
            Base64.getEncoder().encodeToString("my_super_secure_jwt_secret_key_1234567890".getBytes());
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("a_completely_different_secret_key_9876543".getBytes());

    private final JwtUtil jwtUtil = new JwtUtil(SECRET);

    @Test
    void generateToken_thenGetEmailFromToken_roundTrips() {
        String token = jwtUtil.generateToken("user@example.com");

        assertNotNull(token);
        assertEquals("user@example.com", jwtUtil.getEmailFromToken(token));
    }

    @Test
    void validateToken_acceptsFreshlyGeneratedToken() {
        String token = jwtUtil.generateToken("user@example.com");

        assertDoesNotThrow(() -> jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_throwsOnTamperedToken() {
        String token = jwtUtil.generateToken("user@example.com");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThrows(JwtException.class, () -> jwtUtil.validateToken(tampered));
    }

    @Test
    void validateToken_throwsOnTokenSignedWithDifferentKey() {
        String foreignToken = new JwtUtil(OTHER_SECRET).generateToken("user@example.com");

        assertThrows(JwtException.class, () -> jwtUtil.validateToken(foreignToken));
    }

    @Test
    void validateToken_throwsOnGarbage() {
        assertThrows(JwtException.class, () -> jwtUtil.validateToken("not-a-jwt"));
    }

    @Test
    void getEmailFromToken_throwsOnTokenSignedWithDifferentKey() {
        String foreignToken = new JwtUtil(OTHER_SECRET).generateToken("user@example.com");

        assertThrows(JwtException.class, () -> jwtUtil.getEmailFromToken(foreignToken));
    }
}
