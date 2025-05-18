package com.safetrack.api.model;

/**
 * Enum representing the status of an emergency contact relationship.
 */
public enum EmergencyContactStatus {
    /**
     * Contact has been invited but has not yet responded.
     */
    PENDING,
    
    /**
     * Contact has accepted the invitation and is actively serving as an emergency contact.
     */
    ACTIVE,
    
    /**
     * Contact has declined the invitation to be an emergency contact.
     */
    DECLINED,
    
    /**
     * The emergency contact relationship has been revoked by the user.
     */
    REVOKED,
    
    /**
     * The emergency contact relationship has expired (e.g., token expired without response).
     */
    EXPIRED
}

