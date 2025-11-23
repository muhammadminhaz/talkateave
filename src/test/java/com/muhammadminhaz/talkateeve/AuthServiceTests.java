package com.muhammadminhaz.talkateeve;

import com.muhammadminhaz.talkateeve.dto.LoginRequestDTO;
import com.muhammadminhaz.talkateeve.model.User;
import com.muhammadminhaz.talkateeve.service.AuthService;
import com.muhammadminhaz.talkateeve.service.UserService;
import com.muhammadminhaz.talkateeve.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private AuthService authService;

    @Test
    void authenticate_ShouldReturnTokenMap_WhenCredentialsAreValid() {
        // Arrange
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("encodedPass");

        when(userService.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encodedPass"))
                .thenReturn(true);
        when(jwtUtil.generateToken("test@example.com"))
                .thenReturn("fake_jwt_token");

        // Act
        Map<String, Object> result = authService.authenticate(request, response);

        // Assert
        assertNotNull(result);
        assertTrue((Boolean) result.get("success"));
        assertEquals("fake_jwt_token", result.get("token"));
        assertEquals("Login successful", result.get("message"));

        // Verify header is set
        verify(response, times(1)).setHeader(eq("Set-Cookie"), contains("token=fake_jwt_token"));
    }

    @Test
    void authenticate_ShouldReturnFailureMap_WhenUserNotFound() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("missing@example.com");
        request.setPassword("pass");

        when(userService.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        Map<String, Object> result = authService.authenticate(request, response);

        assertNotNull(result);
        assertFalse((Boolean) result.get("success"));
        assertEquals("Invalid credentials", result.get("message"));

        verify(response, never()).setHeader(anyString(), anyString());
    }

    @Test
    void authenticate_ShouldReturnFailureMap_WhenPasswordDoesNotMatch() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("test@example.com");
        request.setPassword("wrong");

        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("encodedPass");

        when(userService.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encodedPass"))
                .thenReturn(false);

        Map<String, Object> result = authService.authenticate(request, response);

        assertNotNull(result);
        assertFalse((Boolean) result.get("success"));
        assertEquals("Invalid credentials", result.get("message"));

        verify(response, never()).setHeader(anyString(), anyString());
    }
}
