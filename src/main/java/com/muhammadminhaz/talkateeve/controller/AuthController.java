package com.muhammadminhaz.talkateeve.controller;

import com.muhammadminhaz.talkateeve.dto.*;
import com.muhammadminhaz.talkateeve.model.User;
import com.muhammadminhaz.talkateeve.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        try {
            User user = authService.register(request).orElseThrow();
            return ResponseEntity.ok(new RegisterResponseDTO("Registration successful", user.getEmail()));
        } catch (IllegalArgumentException ex) {
            // Handled locally rather than by GlobalExceptionHandler because the frontend
            // depends on the typed RegisterResponseDTO body. Log so it is not invisible.
            log.warn("Registration rejected for email={}: {}", request.getEmail(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new RegisterResponseDTO(ex.getMessage(), request.getEmail()));
        }
    }

    @Operation(summary = "Login user and get JWT token")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO loginRequestDTO, HttpServletResponse response) {
        Map<String, Object> result = authService.authenticate(loginRequestDTO, response);

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }


    @Operation(summary = "Validate JWT Token and get user")
    @GetMapping("/me")
    public ResponseEntity<UserDTO> validateToken(@CookieValue(value = "token", required = false) String token) {
        // Never log the token itself - it is a live credential.
        log.debug("/auth/me called (token present: {})", token != null);
        if (token == null || !authService.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        User user = authService.getUserFromToken(token);
        if (user == null) {
            // Signature is valid but the account is gone. Without this the next line NPEs
            // into a 500 that looks like a server fault instead of a stale session.
            log.warn("/auth/me: valid token for a user that no longer exists");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserDTO userDto = new UserDTO(user.getId().toString(), user.getUsername(), user.getEmail());
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        // Clear the HttpOnly cookie with the same attributes used when setting it
        response.setHeader("Set-Cookie",
                "token=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=None");

        return ResponseEntity.ok().build();
    }
}
