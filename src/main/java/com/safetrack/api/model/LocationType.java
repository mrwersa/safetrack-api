package com.safetrack.api.model;

/**
 * Enum representing different types of location data.
 * This helps categorize location data based on how it was obtained.
 */
public enum LocationType {
    /**
     * Location determined by GPS hardware.
     * Generally the most accurate method.
     */
    GPS,
    
    /**
     * Location determined by cell tower triangulation or WiFi networks.
     * Less accurate than GPS but works indoors.
     */
    NETWORK,
    
    /**
     * Location manually entered by the user.
     */
    MANUAL,
    
    /**
     * Location obtained during an emergency/SOS situation.
     * Often prioritized in emergency response scenarios.
     */
    EMERGENCY,
    
    /**
     * Location obtained from a geofence event.
     */
    GEOFENCE,
    
    /**
     * Location from an unspecified or other source.
     */
    OTHER,
    
    /**
     * Location from a home environment.
     */
    HOME,
    
    /**
     * Location from a work environment.
     */
    WORK,
    
    /**
     * Location from a school environment.
     */
    SCHOOL
}

