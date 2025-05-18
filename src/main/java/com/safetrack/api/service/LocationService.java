package com.safetrack.api.service;

import com.safetrack.api.model.Location;
import com.safetrack.api.model.LocationType;
import com.safetrack.api.model.User;
import com.safetrack.api.repository.LocationRepository;
import com.safetrack.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user location data.
 * Provides methods for recording, retrieving, and analyzing location data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    /**
     * Records a new location for a user.
     *
     * @param userId The ID of the user
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     * @param timestamp The time when the location was recorded (optional, defaults to now)
     * @param accuracy The accuracy of the location in meters (optional)
     * @param altitude The altitude in meters (optional)
     * @param locationType The type of location data (optional, defaults to OTHER)
     * @param isEmergency Whether this is an emergency location (optional, defaults to false)
     * @param notes Optional notes about this location
     * @return The saved Location object
     * @throws IllegalArgumentException if the user doesn't exist or coordinates are invalid
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional
    public Location recordLocation(
            UUID userId,
            Double latitude,
            Double longitude,
            LocalDateTime timestamp,
            Double accuracy,
            Double altitude,
            LocationType locationType,
            Boolean isEmergency,
            String notes
    ) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        // Security check - only allow users to record their own location or admins
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!user.getUsername().equals(currentUsername) && 
                !SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                        .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("Not authorized to record location for this user");
        }

        // Validate coordinates
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }

        // Create location entity
        Location location = Location.builder()
                .user(user)
                .latitude(latitude)
                .longitude(longitude)
                .timestamp(timestamp != null ? timestamp : LocalDateTime.now())
                .accuracy(accuracy)
                .altitude(altitude)
                .locationType(locationType != null ? locationType : LocationType.OTHER)
                .isEmergency(isEmergency != null && isEmergency)
                .notes(notes)
                .build();

        // Log emergency locations
        if (location.isEmergency()) {
            log.warn("Emergency location recorded for user {}: lat={}, lon={}", 
                    user.getUsername(), latitude, longitude);
        } else {
            log.debug("Location recorded for user {}: lat={}, lon={}", 
                    user.getUsername(), latitude, longitude);
        }

        // Save and return
        return locationRepository.save(location);
    }

    /**
     * Records multiple locations for a user in a batch operation.
     *
     * @param userId The ID of the user
     * @param locations List of location data to record
     * @return List of saved Location objects
     * @throws IllegalArgumentException if the user doesn't exist
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional
    public List<Location> recordLocationBatch(UUID userId, List<LocationBatchItem> locations) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        // Security check
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!user.getUsername().equals(currentUsername) && 
                !SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                        .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("Not authorized to record locations for this user");
        }

        List<Location> savedLocations = new ArrayList<>();
        for (LocationBatchItem item : locations) {
            try {
                Location location = Location.builder()
                        .user(user)
                        .latitude(item.getLatitude())
                        .longitude(item.getLongitude())
                        .timestamp(item.getTimestamp() != null ? item.getTimestamp() : LocalDateTime.now())
                        .accuracy(item.getAccuracy())
                        .altitude(item.getAltitude())
                        .locationType(item.getLocationType() != null ? item.getLocationType() : LocationType.OTHER)
                        .isEmergency(item.getIsEmergency() != null && item.getIsEmergency())
                        .notes(item.getNotes())
                        .build();

                savedLocations.add(locationRepository.save(location));
                
                if (location.isEmergency()) {
                    log.warn("Batch: Emergency location recorded for user {}: lat={}, lon={}", 
                            user.getUsername(), item.getLatitude(), item.getLongitude());
                }
            } catch (Exception e) {
                log.error("Error saving location in batch for user {}: {}", userId, e.getMessage());
            }
        }

        log.info("Batch location recording: saved {}/{} locations for user {}", 
                savedLocations.size(), locations.size(), user.getUsername());
        return savedLocations;
    }

    /**
     * Gets the most recent location for a user.
     *
     * @param userId The ID of the user
     * @return Optional containing the most recent location, or empty if none exists
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional(readOnly = true)
    public Optional<Location> getMostRecentLocation(UUID userId) {
        checkUserAccess(userId);
        return locationRepository.findFirstByUserIdOrderByTimestampDesc(userId);
    }

    /**
     * Gets locations for a user with pagination.
     *
     * @param userId The ID of the user
     * @param pageable Pagination information
     * @return Page of locations
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional(readOnly = true)
    public Page<Location> getUserLocations(UUID userId, Pageable pageable) {
        checkUserAccess(userId);
        return locationRepository.findByUserIdOrderByTimestampDesc(userId, pageable);
    }

    /**
     * Gets locations for a user within a specific time range.
     *
     * @param userId The ID of the user
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return List of locations within the time range
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional(readOnly = true)
    public List<Location> getUserLocationsByTimeRange(UUID userId, LocalDateTime startTime, LocalDateTime endTime) {
        checkUserAccess(userId);
        return locationRepository.findByUserIdAndTimestampBetweenOrderByTimestampDesc(userId, startTime, endTime);
    }

    /**
     * Gets emergency locations for a user with pagination.
     *
     * @param userId The ID of the user
     * @param pageable Pagination information
     * @return Page of emergency locations
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional(readOnly = true)
    public Page<Location> getUserEmergencyLocations(UUID userId, Pageable pageable) {
        checkEmergencyAccess(userId);
        return locationRepository.findByUserIdAndIsEmergencyTrueOrderByTimestampDesc(userId, pageable);
    }

    /**
     * Finds locations near a specific point.
     *
     * @param latitude The latitude coordinate of the center point
     * @param longitude The longitude coordinate of the center point
     * @param radiusKm The radius in kilometers to search within
     * @param pageable Pagination information
     * @return Page of nearby locations
     */
    @Transactional(readOnly = true)
    public Page<Location> findNearbyLocations(Double latitude, Double longitude, Double radiusKm, Pageable pageable) {
        return locationRepository.findNearbyLocations(latitude, longitude, radiusKm, pageable);
    }

    /**
     * Finds emergency locations near a specific point.
     *
     * @param latitude The latitude coordinate of the center point
     * @param longitude The longitude coordinate of the center point
     * @param radiusKm The radius in kilometers to search within
     * @param since Only include locations after this time
     * @return List of nearby emergency locations
     */
    @Transactional(readOnly = true)
    public List<Location> findNearbyEmergencies(Double latitude, Double longitude, Double radiusKm, LocalDateTime since) {
        return locationRepository.findNearbyEmergencyLocations(latitude, longitude, radiusKm, since);
    }

    /**
     * Checks if the current user has access to view/modify locations for the specified user.
     */
    private void checkUserAccess(UUID userId) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (!user.getUsername().equals(currentUsername) && 
                !SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                        .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("Not authorized to access locations for this user");
        }
    }

    /**
     * Checks if the current user has access to view emergency locations for the specified user.
     */
    private void checkEmergencyAccess(UUID userId) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        if (!user.getUsername().equals(currentUsername) && 
                !SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                        .stream().anyMatch(a -> a.getAuthority().matches("ROLE_ADMIN|ROLE_EMERGENCY_CONTACT"))) {
            throw new AccessDeniedException("Not authorized to access emergency locations for this user");
        }
    }

    /**
     * Data class for batch location recording.
     */
    public static class LocationBatchItem {
        private Double latitude;
        private Double longitude;
        private LocalDateTime timestamp;
        private Double accuracy;
        private Double altitude;
        private LocationType locationType;
        private Boolean isEmergency;
        private String notes;

        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public Double getAccuracy() { return accuracy; }
        public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }
        public Double getAltitude() { return altitude; }
        public void setAltitude(Double altitude) { this.altitude = altitude; }
        public LocationType getLocationType() { return locationType; }
        public void setLocationType(LocationType locationType) { this.locationType = locationType; }
        public Boolean getIsEmergency() { return isEmergency; }
        public void setIsEmergency(Boolean isEmergency) { this.isEmergency = isEmergency; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public void startEmergencyTracking(User user) {
        // Implementation for starting emergency tracking
    }

    public void stopEmergencyTracking(User user) {
        // Implementation for stopping emergency tracking
    }
}