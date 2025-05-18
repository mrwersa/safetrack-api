package com.safetrack.api.service;

import com.safetrack.api.model.Emergency;
import com.safetrack.api.model.User;
import com.safetrack.api.repository.EmergencyRepository;
import com.safetrack.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class EmergencyServiceTest {

    @Mock
    private EmergencyRepository emergencyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private LocationService locationService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private EmergencyService emergencyService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        emergencyService = new EmergencyService(
                emergencyRepository,
                userRepository,
                notificationService,
                locationService
        );

        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void activateEmergency_Success() {
        // Arrange
        String username = "testuser";
        User user = new User();
        user.setUsername(username);
        user.setId(UUID.randomUUID());

        when(authentication.getName()).thenReturn(username);
        when(userRepository.findByEmail(username)).thenReturn(Optional.of(user));
        when(emergencyRepository.save(any(Emergency.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Emergency result = emergencyService.activateEmergency();

        // Assert
        assertNotNull(result);
        assertTrue(result.isActive());
        assertEquals(user, result.getUser());
        verify(notificationService).notifyEmergencyContacts(user);
        verify(locationService).startEmergencyTracking(user);
    }

    @Test
    void deactivateEmergency_Success() {
        // Arrange
        String username = "testuser";
        User user = new User();
        user.setUsername(username);
        user.setId(UUID.randomUUID());

        Emergency activeEmergency = new Emergency();
        activeEmergency.setUser(user);
        activeEmergency.setActive(true);

        when(authentication.getName()).thenReturn(username);
        when(userRepository.findByEmail(username)).thenReturn(Optional.of(user));
        when(emergencyRepository.findActiveEmergencyByUser(user)).thenReturn(Optional.of(activeEmergency));
        when(emergencyRepository.save(any(Emergency.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Emergency result = emergencyService.deactivateEmergency();

        // Assert
        assertNotNull(result);
        assertFalse(result.isActive());
        assertEquals(user, result.getUser());
        verify(notificationService).notifyEmergencyDeactivated(user);
        verify(locationService).stopEmergencyTracking(user);
    }

    @Test
    void deactivateEmergency_NoActiveEmergency() {
        // Arrange
        String username = "testuser";
        User user = new User();
        user.setUsername(username);
        user.setId(UUID.randomUUID());

        when(authentication.getName()).thenReturn(username);
        when(userRepository.findByEmail(username)).thenReturn(Optional.of(user));
        when(emergencyRepository.findActiveEmergencyByUser(user)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> emergencyService.deactivateEmergency());
        verify(notificationService, never()).notifyEmergencyDeactivated(any());
        verify(locationService, never()).stopEmergencyTracking(any());
    }

    @Test
    void getEmergencyStatus_ReturnsActiveEmergency() {
        // Arrange
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");

        Emergency activeEmergency = new Emergency();
        activeEmergency.setUser(user);
        activeEmergency.setActive(true);

        when(userRepository.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));
        when(emergencyRepository.findActiveEmergencyByUser(user))
            .thenReturn(Optional.of(activeEmergency));

        // Act
        Optional<Emergency> result = emergencyService.getEmergencyStatus();

        // Assert
        assertTrue(result.isPresent());
        assertTrue(result.get().isActive());
    }

    @Test
    void getEmergencyStatus_ReturnsEmpty_WhenNoActiveEmergency() {
        // Arrange
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
            .thenReturn(Optional.of(user));
        when(emergencyRepository.findActiveEmergencyByUser(user))
            .thenReturn(Optional.empty());

        // Act
        Optional<Emergency> result = emergencyService.getEmergencyStatus();

        // Assert
        assertTrue(result.isEmpty());
    }
} 