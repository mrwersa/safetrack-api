package com.safetrack.api.controller;

import com.safetrack.api.model.Location;
import com.safetrack.api.model.LocationType;
import com.safetrack.api.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for location tracking operations.
 * Provides endpoints for recording and retrieving location data.
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Locations", description = "Location tracking and management API")
@SecurityRequirement(name = "bearerAuth")
public class LocationController {

    private final LocationService locationService;

    /**
     * Records a new location for the current user.
     *
     * @param request Location data to record
     * @return The recorded location
     */
    @PostMapping
    @Operation(summary = "Record a new location", description = "Records a new location for the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Location recorded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid location data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<LocationResponse> recordLocation(
            @Valid @RequestBody LocationRequest request,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {
        
        try {
            log.debug("Recording location for user {}: lat={}, lon={}", 
                    userId, request.getLatitude(), request.getLongitude());

            Location location = locationService.recordLocation(
                    userId,
                    request.getLatitude(),
                    request.getLongitude(),
                    request.getTimestamp(),
                    request.getAccuracy(),
                    request.getAltitude(),
                    request.getLocationType(),
                    request.getIsEmergency(),
                    request.getNotes()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(mapToLocationResponse(location));
        } catch (IllegalArgumentException e) {
            log.error("Error recording location: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Records multiple locations in a batch for the current user.
     *
     * @param request Batch of location data to record
     * @return List of recorded locations
     */
    @PostMapping("/batch")
    @Operation(summary = "Record multiple locations", description = "Records multiple locations in a batch for the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Locations recorded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid location data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<BatchLocationResponse> recordLocationBatch(
            @Valid @RequestBody BatchLocationRequest request,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {
        
        try {
            log.debug("Recording batch of {} locations for user {}", 
                    request.getLocations().size(), userId);

            List<LocationService.LocationBatchItem> batchItems = request.getLocations().stream()
                    .map(item -> {
                        LocationService.LocationBatchItem batchItem = new LocationService.LocationBatchItem();
                        batchItem.setLatitude(item.getLatitude());
                        batchItem.setLongitude(item.getLongitude());
                        batchItem.setTimestamp(item.getTimestamp());
                        batchItem.setAccuracy(item.getAccuracy());
                        batchItem.setAltitude(item.getAltitude());
                        batchItem.setLocationType(item.getLocationType());
                        batchItem.setIsEmergency(item.getIsEmergency());
                        batchItem.setNotes(item.getNotes());
                        return batchItem;
                    })
                    .collect(Collectors.toList());

            List<Location> savedLocations = locationService.recordLocationBatch(userId, batchItems);

            BatchLocationResponse response = new BatchLocationResponse();
            response.setCount(savedLocations.size());
            response.setMessage(savedLocations.size() + " locations recorded successfully");
            response.setLocations(savedLocations.stream()
                    .map(this::mapToLocationResponse)
                    .collect(Collectors.toList()));

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Error recording batch locations: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Records an emergency location for the current user.
     * This is a specialized endpoint for emergency situations.
     *
     * @param request Emergency location data
     * @return The recorded location
     */
    @PostMapping("/emergency")
    @Operation(summary = "Record an emergency location", 
            description = "Records an emergency location for the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Emergency location recorded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid location data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<LocationResponse> recordEmergencyLocation(
            @Valid @RequestBody EmergencyLocationRequest request,
            @Parameter(hidden = true) @RequestAttribute("userId") UUID userId) {
        
        try {
            log.warn("Recording EMERGENCY location for user {}: lat={}, lon={}", 
                    userId, request.getLatitude(), request.getLongitude());

            Location location = locationService.recordLocation(
                    userId,
                    request.getLatitude(),
                    request.getLongitude(),
                    LocalDateTime.now(),
                    request.getAccuracy(),
                    null,
                    LocationType.EMERGENCY,
                    true,
                    request.getMessage()
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(mapToLocationResponse(location));
        } catch (IllegalArgumentException e) {
            log.error("Error recording emergency location: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Gets the most recent location for a user.
     *
     * @param userId User ID for whom to get the location
     * @return The most recent location, or 404 if none exists
     */
    @GetMapping("/users/{userId}/current")
    @Operation(summary = "Get most recent location", 
            description = "Gets the most recent location for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Location retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "No location found"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<LocationResponse> getCurrentLocation(
            @PathVariable UUID userId) {
        
        Optional<Location> location = locationService.getMostRecentLocation(userId);
        
        return location
                .map(loc -> ResponseEntity.ok(mapToLocationResponse(loc)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                        "No location found for user " + userId));
    }

    /**
     * Gets location history for a user with pagination.
     *
     * @param userId User ID for whom to get the location history
     * @param pageable Pagination information
     * @return Page of locations
     */
    @GetMapping("/users/{userId}")
    @Operation(summary = "Get location history", 
            description = "Gets location history for a user with pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Locations retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<LocationResponse>> getLocationHistory(
            @PathVariable UUID userId,
            @PageableDefault(size = 20, sort = "timestamp,desc") Pageable pageable) {
        
        Page<Location> locations = locationService.getUserLocations(userId, pageable);
        
        return ResponseEntity.ok(locations.map(this::mapToLocationResponse));
    }

    /**
     * Gets location history for a user within a specific time range.
     *
     * @param userId User ID for whom to get the location history
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return List of locations within the time range
     */
    @GetMapping("/users/{userId}/history")
    @Operation(summary = "Get location history by time range", 
            description = "Gets location history for a user within a specific time range")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Locations retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid time range"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<LocationResponse>> getLocationHistoryByTimeRange(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        
        if (endTime.isBefore(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
        }
        
        List<Location> locations = locationService.getUserLocationsByTimeRange(userId, startTime, endTime);
        
        List<LocationResponse> response = locations.stream()
                .map(this::mapToLocationResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Gets emergency locations for a user.
     *
     * @param userId User ID for whom to get emergency locations
     * @param pageable Pagination information
     * @return Page of emergency locations
     */
    @GetMapping("/users/{userId}/emergency")
    @Operation(summary = "Get emergency locations", 
            description = "Gets emergency locations for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Emergency locations retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<LocationResponse>> getEmergencyLocations(
            @PathVariable UUID userId,
            @PageableDefault(size = 20, sort = "timestamp,desc") Pageable pageable) {
        
        Page<Location> locations = locationService.getUserEmergencyLocations(userId, pageable);
        
        return ResponseEntity.ok(locations.map(this::mapToLocationResponse));
    }

    /**
     * Finds locations near a specific point (admin only).
     *
     * @param latitude The latitude of the center point
     * @param longitude The longitude of the center point
     * @param radius The radius in kilometers
     * @param pageable Pagination information
     * @return Page of locations within the radius
     */
    @GetMapping("/nearby")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Find nearby locations", 
            description = "Finds locations near a specific point (admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Locations retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin only"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<LocationResponse>> getNearbyLocations(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @RequestParam @Positive @Max(1000) Double radius,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<Location> locations = locationService.findNearbyLocations(latitude, longitude, radius, pageable);
        
        return ResponseEntity.ok(locations.map(this::mapToLocationResponse));
    }

    /**
     * Finds emergency locations within a radius (admin and emergency contacts).
     *
     * @param latitude The latitude of the center point
     * @param longitude The longitude of the center point
     * @param radius The radius in kilometers
     * @param hours The number of hours to look back
     * @return List of emergency locations within the radius
     */
    @GetMapping("/emergency/nearby")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMERGENCY_CONTACT')")
    @Operation(summary = "Find nearby emergency locations", 
            description = "Finds emergency locations within a radius (admin and emergency contacts)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Emergency locations retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin or emergency contact only"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<LocationResponse>> getNearbyEmergencyLocations(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            @RequestParam @Positive @Max(1000) Double radius,
            @RequestParam @Positive @Max(168) Integer hours) {
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<Location> locations = locationService.findNearbyEmergencies(latitude, longitude, radius, since);
        
        List<LocationResponse> response = locations.stream()
                .map(this::mapToLocationResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Maps a Location entity to a LocationResponse DTO.
     * 
     * @param location The location entity to map
     * @return The mapped response DTO
     */
    private LocationResponse mapToLocationResponse(Location location) {
        return LocationResponse.builder()
                .id(location.getId())
                .userId(location.getUser().getId())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .timestamp(location.getTimestamp())
                .accuracy(location.getAccuracy())
                .altitude(location.getAltitude())
                .locationType(location.getLocationType())
                .isEmergency(location.isEmergency())
                .notes(location.getNotes())
                .createdAt(location.getCreatedAt())
                .build();
    }
    
    /**
     * Request DTO for recording a new location.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationRequest {
        
        /**
         * Latitude coordinate (from -90 to 90).
         */
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
        @DecimalMax(value = "90.0", message = "Latitude must be at most 90")
        private Double latitude;
        
        /**
         * Longitude coordinate (from -180 to 180).
         */
        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
        @DecimalMax(value = "180.0", message = "Longitude must be at most 180")
        private Double longitude;
        
        /**
         * Timestamp when this location was recorded.
         * If not provided, current time will be used.
         */
        private LocalDateTime timestamp;
        
        /**
         * Accuracy of the location in meters (smaller is better).
         */
        private Double accuracy;
        
        /**
         * Altitude in meters above sea level.
         */
        private Double altitude;
        
        /**
         * Type of the location data source.
         */
        private LocationType locationType;
        
        /**
         * Flag indicating if this is an emergency location.
         */
        private Boolean isEmergency;
        
        /**
         * Optional additional information about this location.
         */
        @Size(max = 500, message = "Notes must be less than 500 characters")
        private String notes;
    }
    
    /**
     * Request DTO for recording an emergency location.
     * Simplified version of LocationRequest with emergency-specific fields.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmergencyLocationRequest {
        
        /**
         * Latitude coordinate (from -90 to 90).
         */
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
        @DecimalMax(value = "90.0", message = "Latitude must be at most 90")
        private Double latitude;
        
        /**
         * Longitude coordinate (from -180 to 180).
         */
        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
        @DecimalMax(value = "180.0", message = "Longitude must be at most 180")
        private Double longitude;
        
        /**
         * Accuracy of the location in meters (smaller is better).
         */
        private Double accuracy;
        
        /**
         * Emergency message or details.
         */
        @Size(max = 500, message = "Message must be less than 500 characters")
        private String message;
    }
    
    /**
     * Request DTO for batch location recording.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchLocationRequest {
        
        /**
         * List of locations to record in the batch.
         */
        @NotEmpty(message = "At least one location is required")
        @Size(max = 100, message = "Maximum 100 locations per batch")
        private List<LocationRequest> locations;
    }
    
    /**
     * Response DTO for location data.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Location data response")
    public static class LocationResponse {
        
        /**
         * Unique identifier for the location record.
         */
        private UUID id;
        
        /**
         * ID of the user associated with this location.
         */
        private UUID userId;
        
        /**
         * Latitude coordinate.
         */
        private Double latitude;
        
        /**
         * Longitude coordinate.
         */
        private Double longitude;
        
        /**
         * Timestamp when this location was recorded.
         */
        private LocalDateTime timestamp;
        
        /**
         * Accuracy of the location in meters.
         */
        private Double accuracy;
        
        /**
         * Altitude in meters above sea level.
         */
        private Double altitude;
        
        /**
         * Type of the location data source.
         */
        private LocationType locationType;
        
        /**
         * Flag indicating if this is an emergency location.
         */
        private Boolean isEmergency;
        
        /**
         * Optional additional information about this location.
         */
        private String notes;
        
        /**
         * Timestamp when the location record was created in the database.
         */
        private LocalDateTime createdAt;
    }
    
    /**
     * Response DTO for batch location recording.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Batch location operation response")
    public static class BatchLocationResponse {
        
        /**
         * Number of locations successfully recorded.
         */
        private Integer count;
        
        /**
         * Operation result message.
         */
        private String message;
        
        /**
         * List of recorded locations.
         */
        private List<LocationResponse> locations;
    }
}

