package com.safetrack.api.repository;

import com.safetrack.api.model.EmergencyContact;
import com.safetrack.api.model.EmergencyContactStatus;
import com.safetrack.api.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for EmergencyContact entity operations.
 * Provides methods for CRUD operations and custom queries for emergency contact data.
 */
@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, UUID> {

    /**
     * Finds all emergency contacts for a specific user.
     *
     * @param user The user whose emergency contacts to retrieve
     * @return List of emergency contacts
     */
    List<EmergencyContact> findByUser(User user);

    /**
     * Finds all emergency contacts for a specific user with pagination.
     *
     * @param user The user whose emergency contacts to retrieve
     * @param pageable Pagination information
     * @return Page of emergency contacts
     */
    Page<EmergencyContact> findByUser(User user, Pageable pageable);

    /**
     * Finds all emergency contacts for a specific user ID.
     *
     * @param userId The ID of the user whose emergency contacts to retrieve
     * @return List of emergency contacts
     */
    List<EmergencyContact> findByUserId(UUID userId);

    /**
     * Finds all emergency contacts for a specific user by their ID with pagination.
     *
     * @param userId The ID of the user whose emergency contacts to retrieve
     * @param pageable Pagination information
     * @return Page of emergency contacts
     */
    Page<EmergencyContact> findByUserId(UUID userId, Pageable pageable);

    /**
     * Finds all emergency contacts for a user with a specific status.
     *
     * @param userId The ID of the user whose emergency contacts to retrieve
     * @param status The status to filter by
     * @return List of emergency contacts with the specified status
     */
    List<EmergencyContact> findByUserIdAndStatus(UUID userId, EmergencyContactStatus status);

    /**
     * Finds all active emergency contacts for a user.
     *
     * @param userId The ID of the user whose active emergency contacts to retrieve
     * @return List of active emergency contacts
     */
    default List<EmergencyContact> findActiveContactsByUserId(UUID userId) {
        return findByUserIdAndStatus(userId, EmergencyContactStatus.ACTIVE);
    }

    /**
     * Finds all pending emergency contacts for a user.
     *
     * @param userId The ID of the user whose pending emergency contacts to retrieve
     * @return List of pending emergency contacts
     */
    default List<EmergencyContact> findPendingContactsByUserId(UUID userId) {
        return findByUserIdAndStatus(userId, EmergencyContactStatus.PENDING);
    }

    /**
     * Finds emergency contacts by the verification token.
     *
     * @param token The verification token to look up
     * @return Optional containing the emergency contact if found, or empty if not found
     */
    Optional<EmergencyContact> findByVerificationToken(String token);

    /**
     * Finds pending emergency contacts with a verification token that has not expired.
     *
     * @param token The verification token to look up
     * @param statusPending The status should be PENDING
     * @param expiryDateTime Tokens created before this time are considered expired
     * @return Optional containing the emergency contact if found and valid, or empty if not
     */
    Optional<EmergencyContact> findByVerificationTokenAndStatusAndTokenCreatedAtAfter(
            String token, EmergencyContactStatus statusPending, LocalDateTime expiryDateTime);

    /**
     * Convenience method to find a valid pending token.
     *
     * @param token The verification token to look up
     * @param expiryDateTime Tokens created before this time are considered expired
     * @return Optional containing the emergency contact if found and valid, or empty if not
     */
    default Optional<EmergencyContact> findByValidPendingToken(String token, LocalDateTime expiryDateTime) {
        return findByVerificationTokenAndStatusAndTokenCreatedAtAfter(
                token, EmergencyContactStatus.PENDING, expiryDateTime);
    }

    /**
     * Finds emergency contacts where the contact's user account matches the provided user.
     *
     * @param contactUser The user account of the contact
     * @return List of emergency contacts where this user is designated as a contact
     */
    List<EmergencyContact> findByContactUser(User contactUser);
    
    /**
     * Finds emergency contacts where the contact's user account matches the provided user with pagination.
     *
     * @param contactUser The user account of the contact
     * @param pageable Pagination information
     * @return Page of emergency contacts where this user is designated as a contact
     */
    Page<EmergencyContact> findByContactUser(User contactUser, Pageable pageable);

    /**
     * Finds emergency contacts where the contact's user account matches the provided user
     * and the status is ACTIVE.
     *
     * @param contactUser The user account of the contact
     * @return List of active emergency contacts where this user is designated as a contact
     */
    List<EmergencyContact> findByContactUserAndStatus(User contactUser, EmergencyContactStatus status);

    /**
     * Finds all active emergency contacts where the contact's user account matches the provided user.
     *
     * @param contactUser The user account of the contact
     * @return List of active emergency contacts where this user is designated as a contact
     */
    default List<EmergencyContact> findActiveByContactUser(User contactUser) {
        return findByContactUserAndStatus(contactUser, EmergencyContactStatus.ACTIVE);
    }

    /**
     * Checks if an emergency contact relationship already exists between two users.
     *
     * @param userId The ID of the user
     * @param contactUserId The ID of the potential contact's user account
     * @return true if a relationship exists, false otherwise
     */
    boolean existsByUserIdAndContactUserId(UUID userId, UUID contactUserId);

    /**
     * Checks if an emergency contact with the given email already exists for a user.
     *
     * @param userId The ID of the user
     * @param email The email to check
     * @return true if a contact with this email exists, false otherwise
     */
    boolean existsByUserIdAndEmail(UUID userId, String email);

    /**
     * Checks if an emergency contact with the given phone number already exists for a user.
     *
     * @param userId The ID of the user
     * @param phone The phone number to check
     * @return true if a contact with this phone number exists, false otherwise
     */
    boolean existsByUserIdAndPhone(UUID userId, String phone);

    /**
     * Updates the status of an emergency contact.
     *
     * @param id The ID of the emergency contact to update
     * @param status The new status
     * @param updatedAt The timestamp of the update
     * @return The number of records updated
     */
    @Modifying
    @Query("UPDATE EmergencyContact ec SET ec.status = :status, ec.updatedAt = :updatedAt WHERE ec.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") EmergencyContactStatus status, 
                    @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Sets an emergency contact as accepted.
     *
     * @param id The ID of the emergency contact to update
     * @param acceptedAt The timestamp when the contact was accepted
     * @param updatedAt The timestamp of the update
     * @return The number of records updated
     */
    @Modifying
    @Query("UPDATE EmergencyContact ec SET ec.status = 'ACTIVE', ec.acceptedAt = :acceptedAt, " +
           "ec.updatedAt = :updatedAt WHERE ec.id = :id")
    int acceptContact(@Param("id") UUID id, @Param("acceptedAt") LocalDateTime acceptedAt, 
                     @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Finds expired verification tokens that need cleanup.
     *
     * @param expiryDateTime Tokens created before this time are considered expired
     * @param status The status to look for (usually PENDING)
     * @return List of emergency contacts with expired tokens
     */
    List<EmergencyContact> findByStatusAndTokenCreatedAtBefore(
            EmergencyContactStatus status, LocalDateTime expiryDateTime);

    /**
     * Count the number of active emergency contacts for a user.
     *
     * @param userId The ID of the user
     * @return The count of active emergency contacts
     */
    long countByUserIdAndStatus(UUID userId, EmergencyContactStatus status);

    /**
     * Get the count of active emergency contacts for a user.
     *
     * @param userId The ID of the user
     * @return The count of active emergency contacts
     */
    default long countActiveContactsByUserId(UUID userId) {
        return countByUserIdAndStatus(userId, EmergencyContactStatus.ACTIVE);
    }
}

