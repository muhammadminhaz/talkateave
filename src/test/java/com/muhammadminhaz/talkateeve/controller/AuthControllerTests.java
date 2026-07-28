package com.muhammadminhaz.talkateeve.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muhammadminhaz.talkateeve.dto.LoginRequestDTO;
import com.muhammadminhaz.talkateeve.dto.RegisterRequestDTO;
import com.muhammadminhaz.talkateeve.model.User;
import com.muhammadminhaz.talkateeve.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTests {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void register_returns200_whenRegistrationSucceeds() throws Exception {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setUsername("new");
        request.setEmail("new@example.com");
        request.setPassword("secret123");
        request.setConfirmPassword("secret123");

        User user = new User();
        user.setEmail("new@example.com");
        when(authService.register(any(RegisterRequestDTO.class))).thenReturn(Optional.of(user));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    void register_returns400WithReason_whenServiceRejects() throws Exception {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setUsername("taken");
        request.setEmail("taken@example.com");
        request.setPassword("secret123");
        request.setConfirmPassword("secret123");

        when(authService.register(any(RegisterRequestDTO.class)))
                .thenThrow(new IllegalArgumentException("Email already in use"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    @Test
    void register_returns400_whenBeanValidationRejectsTheBody() throws Exception {
        // @Valid was missing, so RegisterRequestDTO's constraints never ran on a request.
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail("not-an-email");
        request.setPassword("a");
        request.setConfirmPassword("a");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any(RegisterRequestDTO.class));
    }

    @Test
    void login_returns401_whenCredentialsInvalid() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("test@example.com");
        request.setPassword("wrong");

        when(authService.authenticate(any(LoginRequestDTO.class), any()))
                .thenReturn(Map.of("success", false, "message", "Invalid credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void login_returns200WithToken_whenCredentialsValid() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("test@example.com");
        request.setPassword("right");

        when(authService.authenticate(any(LoginRequestDTO.class), any()))
                .thenReturn(Map.of("success", true, "token", "jwt-token", "message", "Login successful"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void me_returns401_whenNoCookiePresent() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
        verify(authService, never()).getUserFromToken(anyString());
    }

    @Test
    void me_returns401_whenTokenInvalid() throws Exception {
        when(authService.validateToken("bad")).thenReturn(false);

        mockMvc.perform(get("/auth/me").cookie(new Cookie("token", "bad")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returns401RatherThan500_whenTokenIsValidButUserIsGone() throws Exception {
        when(authService.validateToken("good")).thenReturn(true);
        when(authService.getUserFromToken("good")).thenReturn(null);

        mockMvc.perform(get("/auth/me").cookie(new Cookie("token", "good")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsUser_whenTokenIsValid() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("tester");
        user.setEmail("test@example.com");
        when(authService.validateToken("good")).thenReturn(true);
        when(authService.getUserFromToken("good")).thenReturn(user);

        mockMvc.perform(get("/auth/me").cookie(new Cookie("token", "good")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void logout_clearsCookie() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }
}
