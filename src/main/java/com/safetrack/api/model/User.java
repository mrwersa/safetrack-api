package com.safetrack.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * User entity representing application users.
 * This class stores user authentication data, basic profile information,
 * and security details necessary for Spring Security integration.
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    /**
     * Unique identifier for the user.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique username for authentication.
     */
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Column(nullable = false, unique = true)
    private String username;

    /**
     * User's email address, used for communication and account recovery.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Encrypted password for user authentication.
     */
    @NotBlank(message = "Password is required")
    @Column(nullable = false)
    private String password;

    /**
     * User's first name.
     */
    @Size(max = 50, message = "First name must be less than 50 characters")
    private String firstName;

    /**
     * User's last name.
     */
    @Size(max = 50, message = "Last name must be less than 50 characters")
    private String lastName;

    /**
     * Flag indicating whether the user account is enabled.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    /**
     * Flag indicating whether the user account is not locked.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean accountNonLocked = true;

    /**
     * Collection of roles assigned to the user.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Builder.Default
    private Set<String> roles = new HashSet<>();
    
    /**
     * Collection of emergency contacts designated by this user.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private Set<EmergencyContact> emergencyContacts = new HashSet<>();
    
    /**
     * Collection of users who have designated this user as their emergency contact.
     */
    @OneToMany(mappedBy = "contactUser")
    @Builder.Default
    @ToString.Exclude
    private Set<EmergencyContact> assignedAsContactFor = new HashSet<>();

    /**
     * Timestamp when the user account was created.
     */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the user account was last updated.
     */
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Convenience method to add a role to the user.
     *
     * @param role The role to add
     */
    public void addRole(String role) {
        if (roles == null) {
            roles = new HashSet<>();
        }
        roles.add(role);
    }
}

