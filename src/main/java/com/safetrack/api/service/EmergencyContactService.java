package com.safetrack.api.service;

import com.safetrack.api.model.EmergencyContact;
import com.safetrack.api.model.EmergencyContactStatus;
import com.safetrack.api.model.User;
import com.safetrack.api.repository.EmergencyContactRepository;
import com.safetrack.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing emergency contacts.
 * Provides methods for adding, updating, and removing emergency contacts,
 * as well as handling verification and notifications.
 */
@Service
public class EmergencyContactService {

    @Autowired
    private EmergencyContactRepository emergencyContactRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Value("${safetrack.emergency-contact.max-contacts:5}")
    private int maxContacts;

    @Value("${safetrack.emergency-contact.verification.expiry}")
    private long verificationExpiry;

    @Transactional
    public EmergencyContact addEmergencyContact(String userId, EmergencyContact contact) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if max contacts limit reached
        long activeContacts = emergencyContactRepository.countByUserIdAndStatusIn(
                userId, 
                List.of(EmergencyContactStatus.ACTIVE, EmergencyContactStatus.PENDING)
        );
        if (activeContacts >= maxContacts) {
            throw new IllegalStateException("Maximum number of emergency contacts reached");
        }

        // Check if contact already exists
        Optional<EmergencyContact> existingContact = emergencyContactRepository
                .findByUserIdAndEmail(userId, contact.getEmail());
        if (existingContact.isPresent()) {
            throw new IllegalStateException("Contact already exists");
        }

        // Set initial properties
        contact.setUserId(userId);
        contact.setStatus(EmergencyContactStatus.PENDING);
        contact.setVerificationToken(UUID.randomUUID().toString());
        contact.setCreatedAt(Instant.now().toString());
        contact.setUpdatedAt(Instant.now().toString());

        // Save contact
        EmergencyContact savedContact = emergencyContactRepository.save(contact);

        // Send verification email
        emailService.sendEmergencyContactVerification(
                contact.getEmail(),
                user.getUsername(),
                contact.getVerificationToken()
        );

        return savedContact;
    }

    @Transactional
    public EmergencyContact verifyContact(String token) {
        EmergencyContact contact = emergencyContactRepository.findByVerificationToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        // Check if token has expired
        Instant createdAt = Instant.parse(contact.getCreatedAt());
        if (Instant.now().toEpochMilli() - createdAt.toEpochMilli() > verificationExpiry) {
            contact.setStatus(EmergencyContactStatus.EXPIRED);
            emergencyContactRepository.save(contact);
            throw new IllegalStateException("Verification token has expired");
        }

        // Update contact status
        contact.setStatus(EmergencyContactStatus.ACTIVE);
        contact.setVerificationToken(null);
        contact.setAcceptedAt(Instant.now().toString());
        contact.setUpdatedAt(Instant.now().toString());

        EmergencyContact verifiedContact = emergencyContactRepository.save(contact);

        // Send confirmation emails
        User user = userRepository.findById(contact.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        emailService.sendEmergencyContactVerified(
                contact.getEmail(),
                user.getUsername()
        );

        return verifiedContact;
    }

    @Transactional
    public EmergencyContact updateEmergencyContact(String userId, String contactId, EmergencyContact updates) {
        EmergencyContact contact = emergencyContactRepository.findByIdAndUserId(contactId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        // Update allowed fields
        if (updates.getName() != null) contact.setName(updates.getName());
        if (updates.getRelationship() != null) contact.setRelationship(updates.getRelationship());
        if (updates.getPhoneNumber() != null) contact.setPhoneNumber(updates.getPhoneNumber());
        if (updates.getPriority() > 0) contact.setPriority(updates.getPriority());
        
        contact.setNotifySos(updates.isNotifySos());
        contact.setNotifyGeofence(updates.isNotifyGeofence());
        contact.setNotifyInactivity(updates.isNotifyInactivity());
        contact.setNotifyLowBattery(updates.isNotifyLowBattery());
        
        contact.setUpdatedAt(Instant.now().toString());

        return emergencyContactRepository.save(contact);
    }

    @Transactional
    public void deleteEmergencyContact(String userId, String contactId) {
        EmergencyContact contact = emergencyContactRepository.findByIdAndUserId(contactId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found"));

        // Send notification email to the contact
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        emailService.sendEmergencyContactRemoved(
                contact.getEmail(),
                user.getUsername()
        );

        emergencyContactRepository.delete(contact);
    }

    public List<EmergencyContact> getEmergencyContacts(String userId) {
        return emergencyContactRepository.findByUserId(userId);
    }

    public EmergencyContact getEmergencyContact(String userId, String contactId) {
        return emergencyContactRepository.findByIdAndUserId(contactId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found"));
    }
}

