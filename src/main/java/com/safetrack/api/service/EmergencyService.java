package com.safetrack.api.service;

import com.safetrack.api.model.Emergency;
import com.safetrack.api.model.User;
import com.safetrack.api.repository.EmergencyRepository;
import com.safetrack.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EmergencyService {

    private final EmergencyRepository emergencyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final LocationService locationService;

    @Autowired
    public EmergencyService(
            EmergencyRepository emergencyRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            LocationService locationService) {
        this.emergencyRepository = emergencyRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.locationService = locationService;
    }

    @Transactional
    public Emergency activateEmergency() {
        User user = getCurrentUser();
        
        Emergency emergency = new Emergency();
        emergency.setUser(user);
        emergency.setActive(true);
        
        Emergency savedEmergency = emergencyRepository.save(emergency);
        
        notificationService.notifyEmergencyContacts(user);
        locationService.startEmergencyTracking(user);
        
        return savedEmergency;
    }

    @Transactional
    public Emergency deactivateEmergency() {
        User user = getCurrentUser();
        
        Emergency emergency = emergencyRepository.findActiveEmergencyByUser(user)
                .orElseThrow(() -> new IllegalStateException("No active emergency found"));
        
        emergency.setActive(false);
        Emergency savedEmergency = emergencyRepository.save(emergency);
        
        notificationService.notifyEmergencyDeactivated(user);
        locationService.stopEmergencyTracking(user);
        
        return savedEmergency;
    }

    public Optional<Emergency> getEmergencyStatus() {
        User user = getCurrentUser();
        return emergencyRepository.findActiveEmergencyByUser(user);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
} 