package com.safetrack.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a user's geographical location at a specific point in time.
 * This entity stores coordinates and related data for location tracking features.
 */
@Entity
@Table(name = "locations")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    /**
     * Unique identifier for the location record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user associated with this location record.
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "User is required")
    @ToString.Exclude  // Prevent potential lazy loading issues in toString()
    private User user;

    /**
     * Latitude coordinate of the location.
     * Range: -90 to 90 degrees.
     */
    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
    @DecimalMax(value = "90.0", message = "Latitude must be at most 90")
    @Column(nullable = false)
    private Double latitude;

    /**
     * Longitude coordinate of the location.
     * Range: -180 to 180 degrees.
     */
    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
    @DecimalMax(value = "180.0", message = "Longitude must be at most 180")
    @Column(nullable = false)
    private Double longitude;

    /**
     * Timestamp when this location was recorded.
     * This is separate from the creation timestamp of the database record.
     */
    @NotNull(message = "Timestamp is required")
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * Accuracy of the location in meters.
     * A smaller value indicates more accurate coordinates.
     */
    @Column
    private Double accuracy;

    /**
     * Altitude in meters above sea level, if available.
     */
    @Column
    private Double altitude;

    /**
     * Type of the location data source.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LocationType locationType;

    /**
     * Optional additional information about this location record.
     */
    @Column(length = 500)
    private String notes;

    /**
     * Flag indicating if this location is part of an SOS/emergency.
     */
    @Column(name = "is_emergency")
    private boolean isEmergency;

    /**
     * Timestamp when the location record was created in the database.
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the location record was last updated in the database.
     */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

