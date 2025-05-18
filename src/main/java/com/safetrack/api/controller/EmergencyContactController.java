package com.safetrack.api.controller;

import com.safetrack.api.model.EmergencyContact;
import com.safetrack.api.model.EmergencyContactStatus;
import com.safetrack.api.service.EmergencyContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for emergency contact management operations.
 * Provides endpoints for adding, updating, removing, and verifying emergency contacts.
 */
@RestController
@RequestMapping("/api/emergency-contacts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Emergency Contacts", description = "Emergency contact management API")
@SecurityRequirement(name = "bearerAuth")
public class EmergencyContactController {

    private final EmergencyContactService emergencyContactService;

    /**
     * Gets all emergency contacts for the current user with pagination.
     *
     * @param userId User ID for whom to get the emergency contacts
     * @param pageable Pagination information
     * @return Page of emergency contacts
     */
    @GetMapping("/users/{userId}")
    @Operation(summary = "Get emergency contacts", 
            description = "Gets all emergency contacts for a user with pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Emergency contacts retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<EmergencyContactResponse>> getUserEmergencyContacts(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        try {
            Page<EmergencyContact> contacts = emergencyContactService.getUserEmergencyContacts(userId, pageable);
            
            return ResponseEntity.ok(contacts.map(this::mapToEmergencyContactResponse));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Gets active emergency contacts for the current user.
     *
     * @param userId User ID for whom to get the active emergency contacts
     * @return List of active emergency contacts
     */
    @GetMapping("/users/{userId}/active")
    @Operation(summary = "Get active emergency contacts", 
            description = "Gets active emergency contacts for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active emergency contacts retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<EmergencyContactResponse>> getActiveEmergencyContacts(
            @PathVariable UUID userId) {
        
        try {
            List<EmergencyContact> contacts = emergencyContactService.getActiveEmergencyContacts(userId);
            
            List<EmergencyContactResponse> response = contacts.stream()
                    .map(this::mapToEmergencyContactResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Gets a specific emergency contact.
     *
     * @param contactId ID of the emergency contact to get
     * @return The emergency contact
     */
    @GetMapping("/{contactId}")
    @Operation(summary = "Get emergency contact", 
            description = "Gets a specific emergency contact by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Emergency contact retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Emergency contact not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<EmergencyContactResponse> getEmergencyContact(
            @PathVariable UUID contactId) {
        
        try {
            EmergencyContact contact = emergencyContactService.getEmergencyContact(contactId);
            
            return ResponseEntity.ok(mapToEmergencyContactResponse(contact));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Adds a new emergency contact for a user.
     *
     * @param userId User ID for whom to add the emergency contact
     * @param request Emergency contact information
     * @return The created emergency contact
     */
    @PostMapping("/users/{userId}")
    @Operation(summary = "Add emergency contact", 
            description = "Adds a new emergency contact for a user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Emergency contact created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or contact already exists"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<EmergencyContactResponse> addEmergencyContact(
            @PathVariable UUID userId,
            @Valid @RequestBody EmergencyContactRequest request) {
        
        try {
            EmergencyContact contact = emergencyContactService.addEmergencyContact(
                    userId,
                    request.getName(),
                    request.getEmail(),
                    request.getPhone(),
                    request.getRelationship(),
                    request.getContactUserId(),
                    request.isNotifySos(),
                    request.isNotifyGeofence(),
                    request.isNotifyInactivity(),
                    request.isNotifyLowBattery(),
                    request.getNotes()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(mapToEmergencyContactResponse(contact));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("User not found")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Updates an existing emergency contact.
     *
     * @param contactId ID of the emergency contact to update
     * @param request Updated emergency contact information
     * @return The updated emergency contact
     */
    @PutMapping("/{contactId}")
    @Operation(summary = "Update emergency contact", 
            description = "Updates an existing emergency contact")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Emergency contact updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Emergency contact not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<EmergencyContactResponse> updateEmergencyContact(
            @PathVariable UUID contactId,
            @Valid @RequestBody UpdateEmergencyContactRequest request) {
        
        try {
            EmergencyContact contact = emergencyContactService.updateEmergencyContact(
                    contactId,
                    request.getName(),
                    request.getPhone(),
                    request.getRelationship(),
                    request.getNotifySos(),
                    request.getNotifyGeofence(),
                    request.getNotifyInactivity(),
                    request.getNotifyLowBattery(),
                    request.getNotes()
            );
            
            return ResponseEntity.ok(mapToEmergencyContactResponse(contact));
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Removes an emergency contact.
     *
     * @param contactId ID of the emergency contact to remove
     * @return No content response
     */
    @DeleteMapping("/{contactId}")
    @Operation(summary = "Remove emergency contact", 
            description = "Removes an emergency contact")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Emergency contact removed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Emergency contact not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> removeEmergencyContact(
            @PathVariable UUID contactId) {
        
        try {
            emergencyContactService.removeEmergencyContact(contactId);
            
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Verifies and activates an emergency contact using a verification token.
     * This endpoint is public (no authentication required) to allow non-users to verify.
     *
     * @param token The verification token
     * @return Success response with the activated contact
     */
    @PostMapping("/verify/{token}")
    @Operation(summary = "Verify emergency contact", 
            description = "Verifies and activates an emergency contact using a verification token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Emergency contact verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<VerificationResponse> verifyContact(
            @PathVariable String token) {
        
        try {
            EmergencyContact contact = emergencyContactService.verifyContact(token);
            
            VerificationResponse response = new VerificationResponse(
                    contact.getId(),
                    contact.getUser().getUsername(),
                    contact.getName(),
                    LocalDateTime.now(),
                    "Emergency contact verified and activated successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Declines an emergency contact invitation using a verification token.
     * This endpoint is public (no authentication required) to allow non-users to decline.
     *
     * @param token The verification token
     * @return Success response
     */
    @PostMapping("/decline/{token}")
    @Operation(summary = "Decline emergency contact invitation", 
            description = "Declines an emergency contact invitation using a verification token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invitation declined successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<VerificationResponse> declineContact(
            @PathVariable String token) {
        
        try {
            EmergencyContact contact = emergencyContactService.declineContact(token);
            
            VerificationResponse response = new VerificationResponse(
                    contact.getId(),
                    contact.getUser().getUsername(),
                    contact.getName(),
                    LocalDateTime.now(),
                    "Emergency contact invitation declined successfully"
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Resends a verification token for a pending emergency contact.
     *
     * @param contactId ID of the emergency contact
     * @return Success response
     */
    @PostMapping("/{contactId}/resend")
    @Operation(summary = "Resend verification", 
            description = "Resends a verification token for a pending emergency contact")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification resent successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot resend (not in PENDING status)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Emergency contact not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<MessageResponse> resendVerification(
            @PathVariable UUID contactId) {
        
        try {
            EmergencyContact contact = emergencyContactService.resendVerification(contactId);
            
            MessageResponse response = new MessageResponse(
                    "Verification email resent successfully to " + contact.getEmail());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("not found")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Gets a user's pending contacts information.
     *
     * @param userId User ID
     * @return Information about pending contacts
     */
    @GetMapping("/users/{userId}/pending")
    @Operation(summary = "Get pending contacts info", 
            description = "Gets information about a user's pending emergency contacts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pending contacts information retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Integer>> getPendingContacts(
            @PathVariable UUID userId) {
        
        try {
            Map<String, Integer> pendingInfo = emergencyContactService.checkPendingContacts(userId);
            
            return ResponseEntity.ok(pendingInfo);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Sends emergency notifications to all active emergency contacts.
     *
     * @param userId User ID triggering the emergency
     * @param request Emergency notification details
     * @return Number of contacts notified
     */
    @PostMapping("/users/{userId}/notify-emergency")
    @Operation(summary = "Send emergency notifications", 
            description = "Sends emergency notifications to all active emergency contacts")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notifications sent successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<EmergencyNotificationResponse> sendEmergencyNotifications(
            @PathVariable UUID userId,
            @Valid @RequestBody EmergencyNotificationRequest request) {
        
        try {
            int notifiedCount = emergencyContactService.sendEmergencyNotifications(
                    userId, 
                    request.getLatitude(), 
                    request.getLongitude(), 
                    request.getMessage()
            );
            
            EmergencyNotificationResponse response = new EmergencyNotificationResponse(
                    notifiedCount,
                    LocalDateTime.now(),
                    "Emergency notifications sent to " + notifiedCount + " contacts"
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Gets users who have designated the current user as an emergency contact.
     *
     * @param userId User ID
     * @param pageable Pagination information
     * @return Page of users who have designated this user as a contact
     */
    @GetMapping("/users/{userId}/designated-by")
    @Operation(summary = "Get users designating as contact", 
            description = "Gets users who have designated the current user as an emergency contact")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Designating users retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<EmergencyContactResponse>> getUsersDesignatingAsContact(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        try {
            Page<EmergencyContact> contacts = emergencyContactService.getUsersDesignatingAsContact(userId, pageable);
            
            return ResponseEntity.ok(contacts.map(this::mapToEmergencyContactResponse));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Maps an EmergencyContact entity to an EmergencyContactResponse DTO.
     * 
     * @param contact The emergency contact entity to map
     * @return The mapped response DTO
     */
    private EmergencyContactResponse mapToEmergencyContactResponse(EmergencyContact contact) {
        return EmergencyContactResponse.builder()
                .id(contact.getId())
                .userId(contact.getUser().getId())
                .contactUserId(contact.getContactUser() != null ? contact.getContactUser().getId() : null)
                .name(contact.getName())
                .email(contact.getEmail())
                .phone(contact.getPhone())
                .relationship(contact.getRelationship())
                .status(contact.getStatus())
                .notifySos(contact.getNotifySos())
                .notifyGeofence(contact.getNotifyGeofence())
                .notifyInactivity(contact.getNotifyInactivity())
                .notifyLowBattery(contact.getNotifyLowBattery())
                .notes(contact.getNotes())
                .acceptedAt(contact.getAcceptedAt())
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .build();
    }

    /**
     * Request DTO for creating a new emergency contact.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Emergency contact creation request")
    public static class EmergencyContactRequest {
        
        /**
         * Name of the emergency contact.
         */
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be less than 100 characters")
        private String name;
        
        /**
         * Email of the emergency contact.
         * Required if contactUserId is not provided.
         */
        @Email(message = "Email must be valid")
        @Size(max = 100, message = "Email must be less than 100 characters")
        private String email;
        
        /**
         * Phone number of the emergency contact.
         */
        @Size(max = 20, message = "Phone number must be less than 20 characters")
        private String phone;
        
        /**
         * Relationship to the contact (e.g., "Parent", "Friend").
         */
        @Size(max = 50, message = "Relationship must be less than 50 characters")
        private String relationship;
        
        /**
         * ID of the contact's user account (if they are a user of the system).
         */
        private UUID contactUserId;
        
        /**
         * Whether to notify this contact for SOS alerts.
         */
        private boolean notifySos = true;
        
        /**
         * Whether to notify this contact for geofence boundary alerts.
         */
        private boolean notifyGeofence = false;
        
        /**
         * Whether to notify this contact for inactivity alerts.
         */
        private boolean notifyInactivity = false;
        
        /**
         * Whether to notify this contact for low battery alerts.
         */
        private boolean notifyLowBattery = false;
        
        /**
         * Additional notes about this contact.
         */
        @Size(max = 500, message = "Notes must be less than 500 characters")
        private String notes;
    }
    
    /**
     * Request DTO for updating an existing emergency contact.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Emergency contact update request")
    public static class UpdateEmergencyContactRequest {
        
        /**
         * Updated name of the emergency contact.
         */
        @Size(max = 100, message = "Name must be less than 100 characters")
        private String name;
        
        /**
         * Updated phone number of the emergency contact.
         */
        @Size(max = 20, message = "Phone number must be less than 20 characters")
        private String phone;
        
        /**
         * Updated relationship to the contact.
         */
        @Size(max = 50, message = "Relationship must be less than 50 characters")
        private String relationship;
        
        /**
         * Updated SOS notification preference.
         */
        private Boolean notifySos;
        
        /**
         * Updated geofence notification preference.
         */
        private Boolean notifyGeofence;
        
        /**
         * Updated inactivity notification preference.
         */
        private Boolean notifyInactivity;
        
        /**
         * Updated low battery notification preference.
         */
        private Boolean notifyLowBattery;
        
        /**
         * Updated notes about this contact.
         */
        @Size(max = 500, message = "Notes must be less than 500 characters")
        private String notes;
    }
    
    /**
     * Request DTO for sending emergency notifications.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Emergency notification request")
    public static class EmergencyNotificationRequest {
        
        /**
         * Latitude of the user's current location.
         */
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
        @DecimalMax(value = "90.0", message = "Latitude must be at most 90")
        private Double latitude;
        
        /**
         * Longitude of the user's current location.
         */
        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
        @DecimalMax(value = "180.0", message = "Longitude must be at most 180")
        private Double longitude;
        
        /**
         * Optional emergency message.
         */
        @Size(max = 500, message = "Message must be less than 500 characters")
        private String message;
    }
    
    /**
     * Response DTO for emergency contact data.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Emergency contact response")
    public static class EmergencyContactResponse {
        
        /**
         * ID of the emergency contact.
         */
        private UUID id;
        
        /**
         * ID of the user who designated this emergency contact.
         */
        private UUID userId;
        
        /**
         * ID of the contact's user account (if they are a user of the system).
         */
        private UUID contactUserId;
        
        /**
         * Name of the emergency contact.
         */
        private String name;
        
        /**
         * Email of the emergency contact.
         */
        private String email;
        
        /**
         * Phone number of the emergency contact.
         */
        private String phone;
        
        /**
         * Relationship to the contact.
         */
        private String relationship;
        
        /**
         * Status of the emergency contact.
         */
        private EmergencyContactStatus status;
        
        /**
         * Whether to notify this contact for SOS alerts.
         */
        private Boolean notifySos;
        
        /**
         * Whether to notify this contact for geofence boundary alerts.
         */
        private Boolean notifyGeofence;
        
        /**
         * Whether to notify this contact for inactivity alerts.
         */
        private Boolean notifyInactivity;
        
        /**
         * Whether to notify this contact for low battery alerts.
         */
        private Boolean notifyLowBattery;
        
        /**
         * Additional notes about this contact.
         */
        private String notes;
        
        /**
         * When the contact accepted the invitation.
         */
        private LocalDateTime acceptedAt;
        
        /**
         * When the emergency contact was created.
         */
        private LocalDateTime createdAt;
        
        /**
         * When the emergency contact was last updated.
         */
        private LocalDateTime updatedAt;
    }
    
    /**
     * Response DTO for verification operations.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Verification response")
    public static class VerificationResponse {
        
        /**
         * ID of the emergency contact.
         */
        private UUID contactId;
        
        /**
         * Username of the user who designated this emergency contact.
         */
        private String username;
        
        /**
         * Name of the emergency contact.
         */
        private String contactName;
        
        /**
         * Timestamp of the verification.
         */
        private LocalDateTime timestamp;
        
        /**
         * Result message.
         */
        private String message;
    }
    
    /**
     * Response DTO for simple message responses.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Message response")
    public static class MessageResponse {
        
        /**
         * Response message.
         */
        private String message;
    }
    
    /**
     * Response DTO for emergency notification operations.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Emergency notification response")
    public static class EmergencyNotificationResponse {
        
        /**
         * Number of contacts notified.
         */
        private Integer notifiedCount;
        
        /**
         * Timestamp of the notification.
         */
        private LocalDateTime timestamp;
        
        /**
         * Result message.
         */
        private String message;
    }
}

