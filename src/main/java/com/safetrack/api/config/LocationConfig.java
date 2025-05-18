package com.safetrack.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration class for location tracking functionality.
 * Provides configuration properties and utility beans for location tracking.
 */
@Configuration
@Slf4j
public class LocationConfig {

    /**
     * Creates location properties bean from application.properties.
     * 
     * @return Configured LocationProperties
     */
    @Bean
    @ConfigurationProperties(prefix = "safetrack.location")
    public LocationProperties locationProperties() {
        return new LocationProperties();
    }

    /**
     * Creates a geospatial utility bean for location-related calculations.
     * 
     * @param properties Location properties
     * @return GeospatialUtil instance
     */
    @Bean
    public GeospatialUtil geospatialUtil(LocationProperties properties) {
        log.info("Initializing geospatial utilities with earth radius: {} km", 
                properties.getEarthRadiusKm());
        return new GeospatialUtil(properties.getEarthRadiusKm());
    }

    /**
     * Properties for location tracking configuration.
     * Bound from application.properties with prefix "safetrack.location".
     */
    @Data
    @Validated
    public static class LocationProperties {
        
        /**
         * Maximum number of locations to retrieve in a single request.
         */
        @Positive
        @Max(1000)
        private int maxHistoryResults = 1000;
        
        /**
         * Maximum age (in days) of locations to keep in the database.
         * Older locations may be pruned during maintenance.
         */
        @Positive
        private int maxLocationAgeInDays = 180;
        
        /**
         * Default radius (in km) to use for nearby location searches.
         */
        @Positive
        @Max(1000)
        private double defaultSearchRadiusKm = 5.0;
        
        /**
         * Maximum radius (in km) allowed for location searches.
         */
        @Positive
        @Max(10000)
        private double maxSearchRadiusKm = 100.0;
        
        /**
         * Earth radius in kilometers, used for distance calculations.
         */
        @Positive
        @NotNull
        private double earthRadiusKm = 6371.0;
        
        /**
         * Threshold (in meters) for considering a location as significantly changed.
         * Used to reduce redundant location storage.
         */
        @Positive
        private double significantDistanceThresholdMeters = 10.0;
        
        /**
         * Flag to enable emergency notification webhooks.
         */
        private boolean enableEmergencyNotifications = true;
        
        /**
         * URL to send emergency notifications to (if enabled).
         */
        private String emergencyNotificationUrl = "";
    }

    /**
     * Utility class for geospatial calculations.
     * Provides methods for calculating distances and other location-based operations.
     */
    public static class GeospatialUtil {
        
        private final double earthRadiusKm;
        
        public GeospatialUtil(double earthRadiusKm) {
            this.earthRadiusKm = earthRadiusKm;
        }
        
        /**
         * Calculates the distance between two points using the Haversine formula.
         * 
         * @param lat1 Latitude of first point in degrees
         * @param lon1 Longitude of first point in degrees
         * @param lat2 Latitude of second point in degrees
         * @param lon2 Longitude of second point in degrees
         * @return Distance in kilometers
         */
        public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
            // Convert latitude and longitude from degrees to radians
            double latDistance = Math.toRadians(lat2 - lat1);
            double lonDistance = Math.toRadians(lon2 - lon1);
            
            // Haversine formula calculation
            double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
            
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            
            return earthRadiusKm * c;
        }
        
        /**
         * Checks if a location point is within the specified radius of a center point.
         * 
         * @param centerLat Latitude of center point in degrees
         * @param centerLon Longitude of center point in degrees
         * @param pointLat Latitude of the point to check in degrees
         * @param pointLon Longitude of the point to check in degrees
         * @param radiusKm Radius in kilometers
         * @return true if the point is within the radius, false otherwise
         */
        public boolean isWithinRadius(double centerLat, double centerLon, 
                                     double pointLat, double pointLon, double radiusKm) {
            double distance = calculateDistance(centerLat, centerLon, pointLat, pointLon);
            return distance <= radiusKm;
        }
        
        /**
         * Calculates a bounding box around a center point based on a radius.
         * This can be used to optimize database queries by first filtering by bounding box.
         * 
         * @param centerLat Latitude of center point in degrees
         * @param centerLon Longitude of center point in degrees
         * @param radiusKm Radius in kilometers
         * @return Array containing [minLat, minLon, maxLat, maxLon]
         */
        public double[] calculateBoundingBox(double centerLat, double centerLon, double radiusKm) {
            // Rough approximation of degrees latitude per km
            double latDegPerKm = 1.0 / 110.574;
            
            // Rough approximation of degrees longitude per km at this latitude
            double lonDegPerKm = 1.0 / (111.320 * Math.cos(Math.toRadians(centerLat)));
            
            // Calculate the bounding box
            double latDelta = radiusKm * latDegPerKm;
            double lonDelta = radiusKm * lonDegPerKm;
            
            double minLat = centerLat - latDelta;
            double maxLat = centerLat + latDelta;
            double minLon = centerLon - lonDelta;
            double maxLon = centerLon + lonDelta;
            
            // Ensure coordinates are within valid ranges
            minLat = Math.max(minLat, -90.0);
            maxLat = Math.min(maxLat, 90.0);
            minLon = Math.max(minLon, -180.0);
            maxLon = Math.min(maxLon, 180.0);
            
            return new double[] { minLat, minLon, maxLat, maxLon };
        }
    }
}

