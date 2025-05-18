package com.safetrack.api.model;

/**
 * Enum representing the standard roles available in the SafeTrack application.
 * These roles determine user access levels and permissions within the system.
 */
public enum Role {
    /**
     * Default role assigned to all registered users.
     * Provides access to basic application features including location tracking,
     * emergency alerts, and contact management.
     */
    USER,

    /**
     * Administrative role with elevated privileges.
     * System administrators can manage users, view system statistics,
     * and configure system-wide settings.
     */
    ADMIN,

    /**
     * Role assigned to trusted emergency contacts.
     * Users with this role can receive emergency notifications
     * and access location data when an SOS alert is triggered.
     */
    EMERGENCY_CONTACT;

    /**
     * Returns the role name with the standard "ROLE_" prefix used by Spring Security.
     * 
     * @return The role name with "ROLE_" prefix
     */
    public String getAuthority() {
        return "ROLE_" + this.name();
    }
}

