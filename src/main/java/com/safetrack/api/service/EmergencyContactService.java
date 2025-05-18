package com.safetrack.api.service;

import com.safetrack.api.model.EmergencyContact;
import com.safetrack.api.model.EmergencyContactStatus;
import com.safetrack.api.model.User;
import com.safetrack.api.repository.EmergencyContactRepository;
import com.safetrack.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing emergency contacts.
 * Provides methods for adding, updating, and removing emergency contacts,
 * as well as handling verification and notifications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyContactService {

    private final EmergencyContactRepository emergencyContactRepository;
    private final UserRepository userRepository;
    // Inject email service for notifications when available
    // private final EmailService emailService;
    
    @Value("${safetrack.emergency-contact.token-expiry-days:7}")
    private int tokenExpiryDays;
    
    @Value("${safetrack.emergency-contact.max-contacts:5}")
    private int maxEmergencyContacts;

    /**
     * Adds a new emergency contact for a user.
     *
     * @param userId The ID of the user adding the contact
     * @param name Name of the emergency contact
     * @param email Email of the emergency contact (optional if contactUserId is provided)
     * @param phone Phone number of the emergency contact (optional)
     * @param relationship Relationship to the contact (optional)
     * @param contactUserId ID of the contact's user account if they are a user (optional)
     * @param notifySos Whether to notify for SOS alerts
     * @param notifyGeofence Whether to notify for geofence alerts
     * @param notifyInactivity Whether to notify for inactivity alerts
     * @param notifyLowBattery Whether to notify for low battery alerts
     * @param notes Additional notes about this contact (optional)
     * @return The created emergency contact
     * @throws IllegalArgumentException if the user doesn't exist or inputs are invalid
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional
    public EmergencyContact addEmergencyContact(
            UUID userId,
            String name,
            String email,
            String phone,
            String relationship,
            UUID contactUserId,
            boolean notifySos,
            boolean notifyGeofence,
            boolean notifyInactivity,
            boolean notifyLowBattery,
            String notes
    ) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        // Security check - only allow users to manage their own contacts or admins
        checkUserAccess(user);
        
        // Validate contact limit
        long activeContacts = emergencyContactRepository.countActiveContactsByUserId(userId);
        if (activeContacts >= maxEmergencyContacts) {
            throw new IllegalArgumentException("Maximum number of emergency contacts (" + maxEmergencyContacts + ") reached");
        }
        
        // Basic validation
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Contact name is required");
        }
        
        // Set up contact user if provided
        User contactUser = null;
        if (contactUserId != null) {
            contactUser = userRepository.findById(contactUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Contact user not found with ID: " + contactUserId));
            
            // Check if this relationship already exists
            if (emergencyContactRepository.existsByUserIdAndContactUserId(userId, contactUserId)) {
                throw new IllegalArgumentException("Emergency contact relationship already exists with this user");
            }
        } else {
            // If no contact user ID, email is required
            if (email == null || email.trim().isEmpty()) {
                throw new IllegalArgumentException("Email is required when contact is not a registered user");
            }
            
            // Check if email already exists for this user's contacts
            if (email != null && !email.trim().isEmpty() && 
                    emergencyContactRepository.existsByUserIdAndEmail(userId, email.trim())) {
                throw new IllegalArgumentException("Emergency contact with this email already exists");
            }
        }
        
        // Check if phone already exists for this user's contacts
        if (phone != null && !phone.trim().isEmpty() && 
                emergencyContactRepository.existsByUserIdAndPhone(userId, phone.trim())) {
            throw new IllegalArgumentException("Emergency contact with this phone number already exists");
        }

        // Create emergency contact entity
        EmergencyContact emergencyContact = EmergencyContact.builder()
                .user(user)
                .contactUser(contactUser)
                .name(name.trim())
                .email(email != null ? email.trim() : null)
                .phone(phone != null ? phone.trim() : null)
                .relationship(relationship != null ? relationship.trim() : null)
                .status(EmergencyContactStatus.PENDING)
                .notifySos(notifySos)
                .notifyGeofence(notifyGeofence)
                .notifyInactivity(notifyInactivity)
                .notifyLowBattery(notifyLowBattery)
                .notes(notes != null ? notes.trim() : null)
                .build();
        
        // Generate verification token if not adding an existing user as contact
        if (contactUser == null) {
            emergencyContact.generateVerificationToken();
        }
        
        // Save the emergency contact
        EmergencyContact savedContact = emergencyContactRepository.save(emergencyContact);
        log.info("Emergency contact created: {} for user {}", savedContact.getId(), user.getUsername());
        
        // Send invitation email if email is available
        // This would be implemented with an email service
        // if (email != null && !email.trim().isEmpty()) {
        //     sendContactInvitationEmail(savedContact);
        // }
        
        return savedContact;
    }

    /**
     * Updates an existing emergency contact.
     *
     * @param contactId The ID of the emergency contact to update
     * @param name Updated name (optional)
     * @param phone Updated phone number (optional)
     * @param relationship Updated relationship (optional)
     * @param notifySos Updated SOS notification preference
     * @param notifyGeofence Updated geofence notification preference
     * @param notifyInactivity Updated inactivity notification preference
     * @param notifyLowBattery Updated low battery notification preference
     * @param notes Updated notes (optional)
     * @return The updated emergency contact
     * @throws IllegalArgumentException if the contact doesn't exist or inputs are invalid
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional
    public EmergencyContact updateEmergencyContact(
            UUID contactId,
            String name,
            String phone,
            String relationship,
            Boolean notifySos,
            Boolean notifyGeofence,
            Boolean notifyInactivity,
            Boolean notifyLowBattery,
            String notes
    ) {
        // Find the emergency contact
        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Emergency contact not found with ID: " + contactId));
        
        // Security check - only allow users to manage their own contacts or admins
        checkUserAccess(contact.getUser());
        
        // Cannot update contacts that are not in ACTIVE status, except for notes
        if (contact.getStatus() != EmergencyContactStatus.ACTIVE && 
                (name != null || phone != null || relationship != null || 
                 notifySos != null || notifyGeofence != null || 
                 notifyInactivity != null || notifyLowBattery != null)) {
            throw new IllegalArgumentException("Can only update active emergency contacts");
        }
        
        // Update fields if provided
        if (name != null && !name.trim().isEmpty()) {
            contact.setName(name.trim());
        }
        
        if (phone != null) {
            // Check if phone already exists for this user's contacts (except this one)
            if (!phone.trim().isEmpty() && 
                    emergencyContactRepository.existsByUserIdAndPhone(contact.getUser().getId(), phone.trim()) && 
                    !phone.trim().equals(contact.getPhone())) {
                throw new IllegalArgumentException("Emergency contact with this phone number already exists");
            }
            contact.setPhone(phone.trim().isEmpty() ? null : phone.trim());
        }
        
        if (relationship != null) {
            contact.setRelationship(relationship.trim().isEmpty() ? null : relationship.trim());
        }
        
        if (notifySos != null) {
            contact.setNotifySos(notifySos);
        }
        
        if (notifyGeofence != null) {
            contact.setNotifyGeofence(notifyGeofence);
        }
        
        if (notifyInactivity != null) {
            contact.setNotifyInactivity(notifyInactivity);
        }
        
        if (notifyLowBattery != null) {
            contact.setNotifyLowBattery(notifyLowBattery);
        }
        
        if (notes != null) {
            contact.setNotes(notes.trim().isEmpty() ? null : notes.trim());
        }
        
        // Save and return the updated contact
        EmergencyContact updatedContact = emergencyContactRepository.save(contact);
        log.info("Emergency contact updated: {}", updatedContact.getId());
        
        return updatedContact;
    }

    /**
     * Removes an emergency contact.
     *
     * @param contactId The ID of the emergency contact to remove
     * @throws IllegalArgumentException if the contact doesn't exist
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional
    public void removeEmergencyContact(UUID contactId) {
        // Find the emergency contact
        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Emergency contact not found with ID: " + contactId));
        
        // Security check - only allow users to manage their own contacts or admins
        checkUserAccess(contact.getUser());
        
        // Delete the contact
        emergencyContactRepository.delete(contact);
        log.info("Emergency contact removed: {}", contactId);
        
        // Notify the contact if they were active
        if (contact.getStatus() == EmergencyContactStatus.ACTIVE && contact.getEmail() != null) {
            // sendContactRemovedEmail(contact);
        }
    }

    /**
     * Gets all emergency contacts for a user.
     *
     * @param userId The ID of the user
     * @param pageable Pagination information
     * @return Page of emergency contacts
     * @throws IllegalArgumentException if the user doesn't exist
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional(readOnly = true)
    public Page<EmergencyContact> getUserEmergencyContacts(UUID userId, Pageable pageable) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        
        // Security check - only allow users to view their own contacts or admins
        checkUserAccess(user);
        
        // Get contacts with pagination
        return emergencyContactRepository.findByUserId(userId, pageable);
    }

    /**
     * Gets active emergency contacts for a user.
     *
     * @param userId The ID of the user
     * @return List of active emergency contacts
     * @throws IllegalArgumentException if the user doesn't exist
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional(readOnly = true)
    public List<EmergencyContact> getActiveEmergencyContacts(UUID userId) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        
        // Security check - only allow users to view their own contacts or admins
        checkUserAccess(user);
        
        // Get active contacts
        return emergencyContactRepository.findActiveContactsByUserId(userId);
    }

    /**
     * Gets a specific emergency contact.
     *
     * @param contactId The ID of the emergency contact
     * @return The emergency contact if found
     * @throws IllegalArgumentException if the contact doesn't exist
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional(readOnly = true)
    public EmergencyContact getEmergencyContact(UUID contactId) {
        // Find the emergency contact
        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Emergency contact not found with ID: " + contactId));
        
        // Security check - only allow users to view their own contacts, contacts where they are the contactUser, or admins
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (isAdmin) {
            return contact;
        }
        
        if (!contact.getUser().getUsername().equals(currentUsername)) {
            // Check if current user is the contactUser
            if (contact.getContactUser() == null || 
                    !contact.getContactUser().getUsername().equals(currentUsername)) {
                throw new AccessDeniedException("Not authorized to access this emergency contact");
            }
        }
        
        return contact;
    }

    /**
     * Verifies and activates an emergency contact using a verification token.
     *
     * @param token The verification token
     * @return The activated emergency contact
     * @throws IllegalArgumentException if the token is invalid or expired
     */
    @Transactional
    public EmergencyContact verifyContact(String token) {
        // Calculate expiry date
        LocalDateTime expiryDate = LocalDateTime.now().minusDays(tokenExpiryDays);
        
        // Find contact with valid token
        EmergencyContact contact = emergencyContactRepository.findByValidPendingToken(token, expiryDate)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));
        
        // Update status to ACTIVE
        contact.setStatus(EmergencyContactStatus.ACTIVE);
        contact.setAcceptedAt(LocalDateTime.now());
        contact.setVerificationToken(null);  // Clear the token
        contact.setTokenCreatedAt(null);
        
        // Save the updated contact
        EmergencyContact activatedContact = emergencyContactRepository.save(contact);
        log.info("Emergency contact verified and activated: {}", activatedContact.getId());
        
        // If the contact is for a registered user, add the EMERGENCY_CONTACT role
        if (contact.getContactUser() != null) {
            User contactUser = contact.getContactUser();
            contactUser.addRole("ROLE_EMERGENCY_CONTACT");
            userRepository.save(contactUser);
            log.info("Added EMERGENCY_CONTACT role to user: {}", contactUser.getUsername());
        }
        
        // Notify the user that their emergency contact has been activated
        // sendContactActivatedEmail(contact.getUser().getEmail(), activatedContact);
        
        return activatedContact;
    }

    /**
     * Declines an emergency contact invitation using a verification token.
     *
     * @param token The verification token
     * @return The declined emergency contact
     * @throws IllegalArgumentException if the token is invalid or expired
     */
    @Transactional
    public EmergencyContact declineContact(String token) {
        // Calculate expiry date
        LocalDateTime expiryDate = LocalDateTime.now().minusDays(tokenExpiryDays);
        
        // Find contact with valid token
        EmergencyContact contact = emergencyContactRepository.findByValidPendingToken(token, expiryDate)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));
        
        // Update status to DECLINED
        contact.setStatus(EmergencyContactStatus.DECLINED);
        contact.setVerificationToken(null);  // Clear the token
        contact.setTokenCreatedAt(null);
        
        // Save the updated contact
        EmergencyContact declinedContact = emergencyContactRepository.save(contact);
        log.info("Emergency contact invitation declined: {}", declinedContact.getId());
        
        // Notify the user that their emergency contact has declined
        // sendContactDeclinedEmail(contact.getUser().getEmail(), declinedContact);
        
        return declinedContact;
    }

    /**
     * Resends a verification token for a pending emergency contact.
     *
     * @param contactId The ID of the emergency contact
     * @return The emergency contact with a new verification token
     * @throws IllegalArgumentException if the contact doesn't exist or isn't in PENDING status
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional
    public EmergencyContact resendVerification(UUID contactId) {
        // Find the emergency contact
        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Emergency contact not found with ID: " + contactId));
        
        // Security check - only allow users to manage their own contacts or admins
        checkUserAccess(contact.getUser());
        
        // Check if contact is in PENDING status
        if (contact.getStatus() != EmergencyContactStatus.PENDING) {
            throw new IllegalArgumentException("Can only resend verification for pending contacts");
        }
        
        // Check if email exists
        if (contact.getEmail() == null || contact.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Contact must have an email to resend verification");
        }
        
        // Generate new verification token
        contact.generateVerificationToken();
        
        // Save the updated contact
        EmergencyContact updatedContact = emergencyContactRepository.save(contact);
        log.info("Verification token regenerated for emergency contact: {}", updatedContact.getId());
        
        // Send new invitation email
        // sendContactInvitationEmail(updatedContact);
        
        return updatedContact;
    }

    /**
     * Cleans up expired verification tokens.
     * This method can be scheduled to run periodically.
     *
     * @return The number of contacts updated to EXPIRED status
     */
    @Transactional
    public int cleanupExpiredTokens() {
        // Calculate expiry date
        LocalDateTime expiryDate = LocalDateTime.now().minusDays(tokenExpiryDays);
        
        // Find all pending contacts with expired tokens
        List<EmergencyContact> expiredContacts = emergencyContactRepository
                .findByStatusAndTokenCreatedAtBefore(EmergencyContactStatus.PENDING, expiryDate);
        
        int count = 0;
        for (EmergencyContact contact : expiredContacts) {
            // Update status to EXPIRED
            contact.setStatus(EmergencyContactStatus.EXPIRED);
            emergencyContactRepository.save(contact);
            count++;
        }
        
        if (count > 0) {
            log.info("Cleaned up {} expired verification tokens", count);
        }
        
        return count;
    }

    /**
     * Sends emergency notifications to all active emergency contacts for a user.
     * This method would be called when an SOS alert is triggered.
     *
     * @param userId The ID of the user triggering the emergency
     * @param latitude The current latitude of the user
     * @param longitude The current longitude of the user
     * @param message Optional emergency message
     * @return The number of contacts notified
     * @throws IllegalArgumentException if the user doesn't exist
     */
    @Transactional(readOnly = true)
    public int sendEmergencyNotifications(UUID userId, Double latitude, Double longitude, String message) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        
        // Get active emergency contacts configured for SOS notifications
        List<EmergencyContact> contacts = emergencyContactRepository.findActiveContactsByUserId(userId);
        
        int notifiedCount = 0;
        for (EmergencyContact contact : contacts) {
            // Only notify contacts that have SOS notifications enabled
            if (contact.getNotifySos()) {
                try {
                    // In a real implementation, this would send SMS, push notifications, emails, etc.
                    // sendEmergencyAlert(contact, user, latitude, longitude, message);
                    log.info("Emergency notification sent to contact: {} for user: {}", 
                            contact.getId(), user.getUsername());
                    notifiedCount++;
                } catch (Exception e) {
                    log.error("Failed to send emergency notification to contact {}: {}", 
                            contact.getId(), e.getMessage());
                }
            }
        }
        
        log.info("Sent emergency notifications to {}/{} contacts for user: {}", 
                notifiedCount, contacts.size(), user.getUsername());
        
        return notifiedCount;
    }

    /**
     * Gets users who have designated the current user as an emergency contact.
     *
     * @param userId The ID of the user who is an emergency contact
     * @param pageable Pagination information
     * @return Page of emergency contact relationships where this user is the contact
     * @throws IllegalArgumentException if the user doesn't exist
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional(readOnly = true)
    public Page<EmergencyContact> getUsersDesignatingAsContact(UUID userId, Pageable pageable) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        
        // Security check - only the user themselves or admins can access this
        checkUserAccess(user);
        
        // Get contacts where this user is the contact with pagination
        return emergencyContactRepository.findByContactUser(user, pageable);
    }

    /**
     * Checks if the current user has access to manage or view a specific user's data.
     * Access is granted if the current user is the same as the user being accessed or if they're an admin.
     * Throws AccessDeniedException if access is denied.
     *
     * @param user The user whose data is being accessed
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    private void checkUserAccess(User user) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        // Allow access if the current user is an admin or the same user
        if (!isAdmin && !user.getUsername().equals(currentUsername)) {
            throw new AccessDeniedException("Not authorized to access this user's data");
        }
    }
    
    /**
     * Checks if the current user has any emergency contacts who need attention.
     * This includes pending invitations sent and received.
     *
     * @param userId The ID of the user to check
     * @return Information about pending emergency contacts
     * @throws IllegalArgumentException if the user doesn't exist
     * @throws AccessDeniedException if the current user doesn't have permission
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> checkPendingContacts(UUID userId) {
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        
        // Security check - only the user themselves or admins can access this
        checkUserAccess(user);
        
        // Get counts of various pending statuses
        Map<String, Integer> result = new HashMap<>();
        
        // Count pending contacts this user has sent invitations for
        List<EmergencyContact> pendingContacts = emergencyContactRepository.findPendingContactsByUserId(userId);
        result.put("pendingSent", pendingContacts.size());
        
        // Count pending invitations where this user is the contact
        if (user.getUsername() != null) {
            List<EmergencyContact> pendingInvitations = emergencyContactRepository.findByContactUserAndStatus(
                    user, EmergencyContactStatus.PENDING);
            result.put("pendingReceived", pendingInvitations.size());
        } else {
            result.put("pendingReceived", 0);
        }
        
        return result;
    }
}

