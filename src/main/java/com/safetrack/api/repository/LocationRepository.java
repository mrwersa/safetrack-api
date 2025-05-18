package com.safetrack.api.repository;

import com.safetrack.api.model.Location;
import com.safetrack.api.model.LocationType;
import com.safetrack.api.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Location entity operations.
 * Provides methods for CRUD operations and custom queries for location data.
 */
@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

    /**
     * Finds all locations for a specific user, ordered by timestamp (newest first).
     *
     * @param user The user whose locations to retrieve
     * @param pageable Pagination information
     * @return Page of locations belonging to the user
     */
    Page<Location> findByUserOrderByTimestampDesc(User user, Pageable pageable);

    /**
     * Finds all locations for a user ID, ordered by timestamp (newest first).
     *
     * @param userId The ID of the user whose locations to retrieve
     * @param pageable Pagination information
     * @return Page of locations belonging to the user
     */
    Page<Location> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);

    /**
     * Gets the most recent location for a specific user.
     *
     * @param userId The ID of the user
     * @return Optional containing the most recent location, or empty if none found
     */
    Optional<Location> findFirstByUserIdOrderByTimestampDesc(UUID userId);

    /**
     * Finds locations for a user within a specific time range.
     *
     * @param userId The ID of the user
     * @param startTime The start of the time range
     * @param endTime The end of the time range
     * @return List of locations within the time range
     */
    List<Location> findByUserIdAndTimestampBetweenOrderByTimestampDesc(
            UUID userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * Finds emergency locations for a user.
     *
     * @param userId The ID of the user
     * @param pageable Pagination information
     * @return Page of emergency locations
     */
    Page<Location> findByUserIdAndIsEmergencyTrueOrderByTimestampDesc(UUID userId, Pageable pageable);

    /**
     * Finds locations of a specific type for a user.
     *
     * @param userId The ID of the user
     * @param locationType The type of location to find
     * @param pageable Pagination information
     * @return Page of locations of the specified type
     */
    Page<Location> findByUserIdAndLocationTypeOrderByTimestampDesc(
            UUID userId, LocationType locationType, Pageable pageable);

    /**
     * Finds locations within a certain distance from a point.
     * Uses the Haversine formula to calculate distance between coordinates.
     *
     * @param latitude The latitude of the center point
     * @param longitude The longitude of the center point
     * @param radius The radius in kilometers
     * @param pageable Pagination information
     * @return Page of locations within the specified radius
     */
    @Query(value = """
            SELECT l FROM Location l 
            WHERE acos(sin(radians(:lat)) * sin(radians(l.latitude)) + 
                      cos(radians(:lat)) * cos(radians(l.latitude)) * 
                      cos(radians(l.longitude) - radians(:lon))) * 6371 <= :radius
            ORDER BY l.timestamp DESC
            """)
    Page<Location> findLocationsWithinRadius(
            @Param("lat") Double latitude,
            @Param("lon") Double longitude,
            @Param("radius") Double radiusKm,
            Pageable pageable);

    /**
     * Finds emergency locations within a radius for a specific time period.
     * Useful for emergency response scenarios.
     *
     * @param latitude The latitude of the center point
     * @param longitude The longitude of the center point
     * @param radius The radius in kilometers
     * @param since The time threshold (only include locations after this time)
     * @return List of emergency locations within the radius
     */
    @Query(value = """
            SELECT l FROM Location l 
            WHERE l.isEmergency = true 
            AND l.timestamp >= :since
            AND acos(sin(radians(:lat)) * sin(radians(l.latitude)) + 
                    cos(radians(:lat)) * cos(radians(l.latitude)) * 
                    cos(radians(l.longitude) - radians(:lon))) * 6371 <= :radius
            ORDER BY l.timestamp DESC
            """)
    List<Location> findNearbyEmergencyLocations(
            @Param("lat") Double latitude,
            @Param("lon") Double longitude,
            @Param("radius") Double radiusKm,
            @Param("since") LocalDateTime since);

    /**
     * Counts the number of locations recorded for a user.
     *
     * @param userId The ID of the user
     * @return The count of locations
     */
    long countByUserId(UUID userId);

    @Query(value = """
            SELECT l FROM Location l
            WHERE acos(sin(radians(:lat)) * sin(radians(l.latitude)) +
                    cos(radians(:lat)) * cos(radians(l.latitude)) *
                    cos(radians(l.longitude) - radians(:lon))) * 6371 <= :radius
            ORDER BY l.timestamp DESC
            """)
    Page<Location> findNearbyLocations(
            @Param("lat") Double latitude,
            @Param("lon") Double longitude,
            @Param("radius") Double radiusKm,
            Pageable pageable);
}

