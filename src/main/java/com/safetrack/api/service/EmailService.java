package com.safetrack.api.service;

import com.safetrack.api.model.EmergencyContact;
import com.safetrack.api.model.User;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface defining email operations for the SafeTrack application.
 * Provides methods for sending various types of emails including
 * verification, notification, and alert emails.
 */
public interface EmailService {

    /**
     * Sends an email with the given subject and content to the recipient.
     *
     * @param to The recipient's email address
     * @param subject The email subject
     * @param content The email content (HTML)
     * @return CompletableFuture representing the asynchronous operation
     */
    CompletableFuture<Void> sendEmail(String to, String subject, String content);

    /**
     * Sends an email with template support, replacing placeholders in the template.
     *
     * @param to The recipient's email address
     * @param subject The email subject
     * @param templateName The name of the template to use
     * @param templateVariables Variables to replace in the template
     * @return CompletableFuture representing the asynchronous operation
     */
    CompletableFuture<Void> sendTemplatedEmail(String to, String subject, String templateName, Map<String, Object> templateVariables);

    /**
     * Sends a verification email to a newly added emergency contact.
     *
     * @param emergencyContact The emergency contact to verify
     * @return CompletableFuture representing the asynchronous operation
     */
    CompletableFuture<Void> sendEmergencyContactVerificationEmail(EmergencyContact emergencyContact);

    /**
     * Sends a notification that the emergency contact has been verified.
     *
     * @param user The user who added the emergency contact
     * @param emergencyContact The emergency contact that was verified
     * @return CompletableFuture representing the asynchronous operation
     */
    CompletableFuture<Void> sendEmergencyContactVerifiedEmail(User user, EmergencyContact emergencyContact);

    /**
     * Sends a notification that an emergency contact declined the invitation.
     *
     * @param user The user who added the emergency contact
     * @param emergencyContact The emergency contact that declined
     * @return CompletableFuture representing the asynchronous operation
     */
    CompletableFuture<Void> sendEmergencyContactDeclinedEmail(User user, EmergencyContact emergencyContact);

    /**
     * Sends an emergency alert to a contact when an SOS is triggered.
     *
     * @param emergencyContact The emergency contact to notify
     * @param location Optional location information (e.g., coordinates or address)
     * @param message Optional custom message from the user
     * @return CompletableFuture representing the asynchronous operation
     */
    CompletableFuture<Void> sendEmergencyAlertEmail(EmergencyContact emergencyContact, String location, String message);

    /**
     * Sends a geofence alert to a contact when a user leaves a defined safe area.
     *
     * @param emergencyContact The emergency contact to notify
     * @param location The location where the user left the safe area
     * @param geofenceName The name of the geofence that was triggered
     * @return CompletableFuture representing the asynchronous operation
     */
    CompletableFuture<Void> sendGeofenceAlertEmail(EmergencyContact emergencyContact, String location, String geofenceName);

    /**
     * Sends an inactivity alert to a contact when a user hasn't been active for a defined period.
     *
     * @param emergencyContact The emergency contact to notify
     * @param lastSeenTime When the user was last seen
     * @return CompletableFuture representing the asynchronous operation
     */
    CompletableFuture<Void> sendInactivityAlertEmail(EmergencyContact emergencyContact, String lastSeenTime);

    /**
     * Sends a low battery alert to a contact when a user's device battery is critically low.
     *
     * @param emergencyContact The emergency contact to notify
     * @param batteryPercentage The current battery percentage
     * @param location Optional last known location
     * @return CompletableFuture representing the asynchronous operation
     */
    CompletableFuture<Void> sendLowBatteryAlertEmail(EmergencyContact emergencyContact, int batteryPercentage, String location);
}

