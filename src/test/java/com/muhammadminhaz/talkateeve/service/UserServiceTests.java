package com.muhammadminhaz.talkateeve.service;

import com.muhammadminhaz.talkateeve.model.User;
import com.muhammadminhaz.talkateeve.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void findByEmail_delegatesToRepository() {
        User user = new User();
        user.setEmail("a@example.com");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByEmail("a@example.com");

        assertTrue(result.isPresent());
        assertEquals("a@example.com", result.get().getEmail());
        verify(userRepository).findByEmail("a@example.com");
    }

    @Test
    void findByEmail_returnsEmptyWhenRepositoryHasNoMatch() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertTrue(userService.findByEmail("missing@example.com").isEmpty());
    }

    @Test
    void save_delegatesToRepository() {
        User user = new User();
        when(userRepository.save(user)).thenReturn(user);

        assertSame(user, userService.save(user));
        verify(userRepository).save(user);
    }

    @Test
    void save_propagatesRepositoryFailure() {
        User user = new User();
        when(userRepository.save(user)).thenThrow(new RuntimeException("constraint violation"));

        assertThrows(RuntimeException.class, () -> userService.save(user));
    }
}
