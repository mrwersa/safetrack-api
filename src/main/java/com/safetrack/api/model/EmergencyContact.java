package com.safetrack.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing an emergency contact for a user.
 * Emergency contacts can be notified during SOS situations and have access to
 * emergency location data of the user who designated them as a contact.
 */
@Entity
@Table(name = "emergency_contacts")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyContact {

    /**
     * Unique identifier for the emergency contact.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The user who designated this emergency contact.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "User is required")
    @ToString.Exclude  // Prevent potential lazy loading issues in toString()
    private User user;

    /**
     * Reference to the contact's user account (if they are also a user of the system).
     * This field is optional since emergency contacts may not be users of the system.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_user_id")
    @ToString.Exclude  // Prevent potential lazy loading issues in toString()
    private User contactUser;

    /**
     * Name of the emergency contact.
     */
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be less than 100 characters")
    @Column(nullable = false)
    private String name;

    /**
     * Phone number of the emergency contact.
     */
    @Size(max = 20, message = "Phone number must be less than 20 characters")
    @Column
    private String phone;

    /**
     * Email address of the emergency contact.
     */
    @Email(message = "Email must be valid")
    @Size(max = 100, message = "Email must be less than 100 characters")
    @Column
    private String email;

    /**
     * Relationship of the contact to the user (e.g., "Parent", "Spouse", "Friend").
     */
    @Size(max = 50, message = "Relationship must be less than 50 characters")
    @Column
    private String relationship;

    /**
     * Status of the emergency contact (e.g., PENDING, ACTIVE, DECLINED).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EmergencyContactStatus status = EmergencyContactStatus.PENDING;

    /**
     * Token used for verification when the contact is not a registered user.
     */
    @Column
    private String verificationToken;

    /**
     * Date when the verification token was created.
     */
    @Column
    private LocalDateTime tokenCreatedAt;

    /**
     * Date when the emergency contact accepted the request.
     */
    @Column
    private LocalDateTime acceptedAt;

    /**
     * Whether to notify this contact for SOS alerts.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean notifySos = true;

    /**
     * Whether to notify this contact for geofence boundary alerts.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean notifyGeofence = false;

    /**
     * Whether to notify this contact for inactivity alerts.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean notifyInactivity = false;

    /**
     * Whether to notify this contact for low battery alerts.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean notifyLowBattery = false;

    /**
     * Additional notes about this emergency contact.
     */
    @Size(max = 500, message = "Notes must be less than 500 characters")
    @Column(length = 500)
    private String notes;

    /**
     * Timestamp when the emergency contact was created.
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the emergency contact was last updated.
     */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Checks if this emergency contact has a valid verification token.
     * 
     * @return true if the token exists and has not expired, false otherwise
     */
    @Transient
    public boolean hasValidToken() {
        if (verificationToken == null || tokenCreatedAt == null) {
            return false;
        }
        
        // Token is valid for 7 days
        return tokenCreatedAt.plusDays(7).isAfter(LocalDateTime.now());
    }

    /**
     * Generates a new verification token for this emergency contact.
     */
    public void generateVerificationToken() {
        this.verificationToken = UUID.randomUUID().toString();
        this.tokenCreatedAt = LocalDateTime.now();
    }
}

