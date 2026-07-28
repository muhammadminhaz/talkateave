package com.muhammadminhaz.talkateeve.service;

import com.muhammadminhaz.talkateeve.dto.LoginRequestDTO;
import com.muhammadminhaz.talkateeve.dto.RegisterRequestDTO;
import com.muhammadminhaz.talkateeve.model.User;
import com.muhammadminhaz.talkateeve.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // Register
    public Optional<User> register(RegisterRequestDTO request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (userService.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User saved = userService.save(user);
        log.info("Registered user id={} email={}", saved.getId(), saved.getEmail());
        return Optional.of(saved);
    }

    public Map<String, Object> authenticate(LoginRequestDTO loginRequestDTO, HttpServletResponse response) {
        return userService
                .findByEmail(loginRequestDTO.getEmail())
                .filter(u -> passwordEncoder.matches(loginRequestDTO.getPassword(), u.getPassword()))
                .map(u -> {
                    String token = jwtUtil.generateToken(u.getEmail());

                    // HttpOnly cookie so the browser never exposes the token to JS
                    response.setHeader("Set-Cookie",
                            String.format("token=%s; Path=/; Max-Age=86400; HttpOnly; Secure; SameSite=None", token));

                    log.info("Login successful for email={}", u.getEmail());

                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("token", token);
                    result.put("message", "Login successful");
                    return result;
                })
                .orElseGet(() -> {
                    log.warn("Login failed for email={}: invalid credentials", loginRequestDTO.getEmail());
                    return Map.of("success", false, "message", "Invalid credentials");
                });
    }

    // Validate JWT
    public boolean validateToken(String token) {
        try {
            jwtUtil.validateToken(token);
            return true;
        } catch (Exception e) {
            // WARN, not ERROR: an expired token is normal traffic on every request.
            // Never log the token itself.
            log.warn("Token validation failed: {}", e.getMessage(), e);
            return false;
        }
    }

    public User getUserFromToken(String token) {
        try {
            String email = jwtUtil.getEmailFromToken(token);
            return userService.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found for email " + email));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Could not resolve user from token: {}", e.getMessage(), e);
            return null;
        }
    }

}
