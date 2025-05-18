package com.safetrack.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safetrack.api.controller.EmergencyContactController;
import com.safetrack.api.model.EmergencyContact;
import com.safetrack.api.model.EmergencyContactStatus;
import com.safetrack.api.model.Role;
import com.safetrack.api.model.User;
import com.safetrack.api.repository.EmergencyContactRepository;
import com.safetrack.api.repository.UserRepository;
import com.safetrack.api.service.EmergencyContactService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the emergency contact functionality.
 * Tests the full lifecycle of emergency contacts including creation,
 * verification, updating, and removal, with actual database interaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class EmergencyContactIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmergencyContactRepository emergencyContactRepository;

    @Autowired
    private EmergencyContactService emergencyContactService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Test data
    private User testUser;
    private User contactUser;
    private EmergencyContact testContact;
    private static final String TEST_PASSWORD = "Password123!";

    @BeforeEach
    void setUp() {
        // Create test users
        testUser = User.builder()
                .username("testuser")
                .email("testuser@example.com")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .firstName("Test")
                .lastName("User")
                .enabled(true)
                .accountNonLocked(true)
                .build();

        contactUser = User.builder()
                .username("contactuser")
                .email("contactuser@example.com")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .firstName("Contact")
                .lastName("User")
                .enabled(true)
                .accountNonLocked(true)
                .build();

        // Add USER role to both users
        testUser.addRole(Role.USER.getAuthority());
        contactUser.addRole(Role.USER.getAuthority());

        // Save users to database
        testUser = userRepository.save(testUser);
        contactUser = userRepository.save(contactUser);
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        emergencyContactRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should complete full emergency contact lifecycle")
    public void testEmergencyContactLifecycle() throws Exception {
        // Step 1: Add emergency contact with email (not registered user)
        EmergencyContactController.EmergencyContactRequest request = new EmergencyContactController.EmergencyContactRequest();
        request.setName("Emergency Contact");
        request.setEmail("emergency@example.com");
        request.setPhone("+1234567890");
        request.setRelationship("Friend");
        request.setNotifySos(true);
        request.setNotifyGeofence(false);
        request.setNotifyInactivity(false);
        request.setNotifyLowBattery(false);

        // Create contact
        MvcResult result = mockMvc.perform(post("/api/emergency-contacts/users/{userId}", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Emergency Contact")))
                .andExpect(jsonPath("$.email", is("emergency@example.com")))
                .andExpect(jsonPath("$.status", is(EmergencyContactStatus.PENDING.name())))
                .andReturn();

        // Extract contact ID from response
        String responseJson = result.getResponse().getContentAsString();
        UUID contactId = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());

        // Verify contact was created in database
        Optional<EmergencyContact> createdContact = emergencyContactRepository.findById(contactId);
        assertTrue(createdContact.isPresent());
        assertEquals(EmergencyContactStatus.PENDING, createdContact.get().getStatus());
        assertNotNull(createdContact.get().getVerificationToken());

        // Extract verification token
        String verificationToken = createdContact.get().getVerificationToken();

        // Step 2: Verify the contact using token
        mockMvc.perform(post("/api/emergency-contacts/verify/{token}", verificationToken)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactId", is(contactId.toString())))
                .andExpect(jsonPath("$.message", containsString("verified and activated")));

        // Verify contact status was updated in database
        Optional<EmergencyContact> verifiedContact = emergencyContactRepository.findById(contactId);
        assertTrue(verifiedContact.isPresent());
        assertEquals(EmergencyContactStatus.ACTIVE, verifiedContact.get().getStatus());
        assertNull(verifiedContact.get().getVerificationToken());
        assertNotNull(verifiedContact.get().getAcceptedAt());

        // Step 3: Update the contact
        EmergencyContactController.UpdateEmergencyContactRequest updateRequest = new EmergencyContactController.UpdateEmergencyContactRequest();
        updateRequest.setName("Updated Contact");
        updateRequest.setPhone("+9876543210");
        updateRequest.setRelationship("Family");
        updateRequest.setNotifySos(true);
        updateRequest.setNotifyGeofence(true);
        updateRequest.setNotes("Emergency contact notes");

        mockMvc.perform(put("/api/emergency-contacts/{contactId}", contactId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Contact")))
                .andExpect(jsonPath("$.phone", is("+9876543210")))
                .andExpect(jsonPath("$.relationship", is("Family")))
                .andExpect(jsonPath("$.notifyGeofence", is(true)))
                .andExpect(jsonPath("$.notes", is("Emergency contact notes")));

        // Verify contact was updated in database
        Optional<EmergencyContact> updatedContact = emergencyContactRepository.findById(contactId);
        assertTrue(updatedContact.isPresent());
        assertEquals("Updated Contact", updatedContact.get().getName());
        assertEquals("+9876543210", updatedContact.get().getPhone());

        // Step 4: Get active emergency contacts
        mockMvc.perform(get("/api/emergency-contacts/users/{userId}/active", testUser.getId())
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(contactId.toString())))
                .andExpect(jsonPath("$[0].name", is("Updated Contact")));

        // Step 5: Remove the contact
        mockMvc.perform(delete("/api/emergency-contacts/{contactId}", contactId)
                .with(csrf()))
                .andExpect(status().isNoContent());

        // Verify contact was removed from database
        Optional<EmergencyContact> deletedContact = emergencyContactRepository.findById(contactId);
        assertFalse(deletedContact.isPresent());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should handle emergency contact with registered user")
    public void testEmergencyContactWithRegisteredUser() throws Exception {
        // Create request with registered user as contact
        EmergencyContactController.EmergencyContactRequest request = new EmergencyContactController.EmergencyContactRequest();
        request.setName("Contact User");
        request.setContactUserId(contactUser.getId());
        request.setRelationship("Colleague");
        request.setNotifySos(true);

        // Create contact
        MvcResult result = mockMvc.perform(post("/api/emergency-contacts/users/{userId}", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Contact User")))
                .andExpect(jsonPath("$.contactUserId", is(contactUser.getId().toString())))
                .andExpect(jsonPath("$.status", is(EmergencyContactStatus.PENDING.name())))
                .andReturn();

        // Extract contact ID from response
        String responseJson = result.getResponse().getContentAsString();
        UUID contactId = UUID.fromString(objectMapper.readTree(responseJson).get("id").asText());

        // Verify the contact (simulating contact user accepting the request)
        Optional<EmergencyContact> pendingContact = emergencyContactRepository.findById(contactId);
        assertTrue(pendingContact.isPresent());
        
        // Simulate verification by admin
        EmergencyContact updatedContact = emergencyContactService.verifyContact(pendingContact.get().getVerificationToken());
        assertEquals(EmergencyContactStatus.ACTIVE, updatedContact.getStatus());
        
        // Verify contact user now has EMERGENCY_CONTACT role
        User updatedContactUser = userRepository.findById(contactUser.getId()).orElseThrow();
        assertTrue(updatedContactUser.getRoles().contains(Role.EMERGENCY_CONTACT.getAuthority()));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should handle emergency notification flow")
    public void testEmergencyNotificationFlow() throws Exception {
        // Create and activate an emergency contact
        EmergencyContact contact = EmergencyContact.builder()
                .user(testUser)
                .name("Emergency Contact")
                .email("emergency@example.com")
                .phone("+1234567890")
                .relationship("Friend")
                .status(EmergencyContactStatus.ACTIVE)
                .notifySos(true)
                .build();
        
        contact = emergencyContactRepository.save(contact);
        
        // Send emergency notification
        EmergencyContactController.EmergencyNotificationRequest notificationRequest = 
                new EmergencyContactController.EmergencyNotificationRequest();
        notificationRequest.setLatitude(40.7128);
        notificationRequest.setLongitude(-74.0060);
        notificationRequest.setMessage("Help! Emergency!");
        
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}/notify-emergency", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(notificationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifiedCount", is(1)))
                .andExpect(jsonPath("$.message", containsString("Emergency notifications sent to 1 contacts")));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should enforce access controls for emergency contacts")
    public void testEmergencyContactAccessControl() throws Exception {
        // Create a contact for test user
        EmergencyContact contact = EmergencyContact.builder()
                .user(testUser)
                .name("Emergency Contact")
                .email("emergency@example.com")
                .phone("+1234567890")
                .relationship("Friend")
                .status(EmergencyContactStatus.ACTIVE)
                .notifySos(true)
                .build();
        
        contact = emergencyContactRepository.save(contact);
        
        // Create a contact for contact user (another user)
        EmergencyContact otherContact = EmergencyContact.builder()
                .user(contactUser)
                .name("Other Contact")
                .email("other@example.com")
                .phone("+1987654321")
                .relationship("Family")
                .status(EmergencyContactStatus.ACTIVE)
                .notifySos(true)
                .build();
        
        otherContact = emergencyContactRepository.save(otherContact);
        
        // Test user should be able to access their own contact
        mockMvc.perform(get("/api/emergency-contacts/{contactId}", contact.getId())
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(contact.getId().toString())));
        
        // Test user should NOT be able to access another user's contact
        mockMvc.perform(get("/api/emergency-contacts/{contactId}", otherContact.getId())
                .with(csrf()))
                .andExpect(status().isForbidden());
        
        // Test user should NOT be able to update another user's contact
        EmergencyContactController.UpdateEmergencyContactRequest updateRequest = 
                new EmergencyContactController.UpdateEmergencyContactRequest();
        updateRequest.setName("Hacked Contact");
        
        mockMvc.perform(put("/api/emergency-contacts/{contactId}", otherContact.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Should allow admin access to any emergency contact")
    public void testEmergencyContactAdminAccess() throws Exception {
        // Create an admin user
        User adminUser = User.builder()
                .username("admin")
                .email("admin@example.com")
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .firstName("Admin")
                .lastName("User")
                .enabled(true)
                .accountNonLocked(true)
                .build();
        
        adminUser.addRole(Role.ADMIN.getAuthority());
        adminUser = userRepository.save(adminUser);
        
        // Create a contact for regular user
        EmergencyContact contact = EmergencyContact.builder()
                .user(testUser)
                .name("Emergency Contact")
                .email("emergency@example.com")
                .phone("+1234567890")
                .relationship("Friend")
                .status(EmergencyContactStatus.ACTIVE)
                .notifySos(true)
                .build();
        
        contact = emergencyContactRepository.save(contact);
        
        // Admin should be able to access any user's contacts
        mockMvc.perform(get("/api/emergency-contacts/{contactId}", contact.getId())
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(contact.getId().toString())))
                .andExpect(jsonPath("$.name", is("Emergency Contact")));
        
        // Admin should be able to update any user's contacts
        EmergencyContactController.UpdateEmergencyContactRequest updateRequest = 
                new EmergencyContactController.UpdateEmergencyContactRequest();
        updateRequest.setName("Admin Updated Contact");
        updateRequest.setNotes("Updated by admin");
        
        mockMvc.perform(put("/api/emergency-contacts/{contactId}", contact.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Admin Updated Contact")))
                .andExpect(jsonPath("$.notes", is("Updated by admin")));
        
        // Admin should be able to access all users' contacts
        mockMvc.perform(get("/api/emergency-contacts/users/{userId}", testUser.getId())
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }
    
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should handle token expiration and cleanup")
    public void testTokenExpirationAndCleanup() throws Exception {
        // Create a contact with expired token by setting creation date in the past
        LocalDateTime expiredTime = LocalDateTime.now().minusDays(10); // 10 days old, beyond typical 7-day expiry
        
        EmergencyContact expiredContact = EmergencyContact.builder()
                .user(testUser)
                .name("Expired Contact")
                .email("expired@example.com")
                .phone("+1234567890")
                .relationship("Friend")
                .status(EmergencyContactStatus.PENDING)
                .verificationToken("expired-token-" + UUID.randomUUID())
                .tokenCreatedAt(expiredTime)
                .notifySos(true)
                .build();
        
        expiredContact = emergencyContactRepository.save(expiredContact);
        
        // Create a contact with valid token
        EmergencyContact validContact = EmergencyContact.builder()
                .user(testUser)
                .name("Valid Contact")
                .email("valid@example.com")
                .phone("+9876543210")
                .relationship("Family")
                .status(EmergencyContactStatus.PENDING)
                .verificationToken("valid-token-" + UUID.randomUUID())
                .tokenCreatedAt(LocalDateTime.now())
                .notifySos(true)
                .build();
        
        validContact = emergencyContactRepository.save(validContact);
        
        // Get valid token
        String validToken = validContact.getVerificationToken();
        
        // Attempt to verify with expired token should fail
        mockMvc.perform(post("/api/emergency-contacts/verify/{token}", expiredContact.getVerificationToken())
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid or expired verification token")));
        
        // Clean up expired tokens
        int expiredCount = emergencyContactService.cleanupExpiredTokens();
        assertEquals(1, expiredCount);
        
        // Verify expired contact status is now EXPIRED
        Optional<EmergencyContact> updatedExpiredContact = emergencyContactRepository.findById(expiredContact.getId());
        assertTrue(updatedExpiredContact.isPresent());
        assertEquals(EmergencyContactStatus.EXPIRED, updatedExpiredContact.get().getStatus());
        
        // Verify with valid token should still work
        mockMvc.perform(post("/api/emergency-contacts/verify/{token}", validToken)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("verified and activated successfully")));
        
        // Valid contact should now be ACTIVE
        Optional<EmergencyContact> updatedValidContact = emergencyContactRepository.findById(validContact.getId());
        assertTrue(updatedValidContact.isPresent());
        assertEquals(EmergencyContactStatus.ACTIVE, updatedValidContact.get().getStatus());
    }
    
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should resend verification token")
    public void testResendVerificationToken() throws Exception {
        // Create a pending contact with token
        EmergencyContact pendingContact = EmergencyContact.builder()
                .user(testUser)
                .name("Pending Contact")
                .email("pending@example.com")
                .phone("+1234567890")
                .relationship("Friend")
                .status(EmergencyContactStatus.PENDING)
                .verificationToken("original-token-" + UUID.randomUUID())
                .tokenCreatedAt(LocalDateTime.now().minusDays(3)) // 3 days old but not expired
                .notifySos(true)
                .build();
        
        pendingContact = emergencyContactRepository.save(pendingContact);
        
        // Save original token info for comparison
        String originalToken = pendingContact.getVerificationToken();
        LocalDateTime originalCreatedAt = pendingContact.getTokenCreatedAt();
        
        // Resend verification
        mockMvc.perform(post("/api/emergency-contacts/{contactId}/resend", pendingContact.getId())
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Verification email resent successfully")));
        
        // Check that token was regenerated
        Optional<EmergencyContact> updatedContact = emergencyContactRepository.findById(pendingContact.getId());
        assertTrue(updatedContact.isPresent());
        assertNotEquals(originalToken, updatedContact.get().getVerificationToken());
        assertTrue(updatedContact.get().getTokenCreatedAt().isAfter(originalCreatedAt));
        
        // Old token should no longer work
        mockMvc.perform(post("/api/emergency-contacts/verify/{token}", originalToken)
                .with(csrf()))
                .andExpect(status().isBadRequest());
        
        // New token should work
        mockMvc.perform(post("/api/emergency-contacts/verify/{token}", updatedContact.get().getVerificationToken())
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("verified and activated successfully")));
    }
    
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should prevent duplicate contacts")
    public void testDuplicateContactPrevention() throws Exception {
        // Create a request
        EmergencyContactController.EmergencyContactRequest request = new EmergencyContactController.EmergencyContactRequest();
        request.setName("Duplicate Contact");
        request.setEmail("duplicate@example.com");
        request.setPhone("+1234567890");
        request.setRelationship("Friend");
        request.setNotifySos(true);
        
        // First creation should succeed
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        
        // Second creation with same email should fail
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        
        // Change email but use same phone
        request.setEmail("different@example.com");
        
        // Creation with duplicate phone should also fail
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        
        // Test duplicate user as contact
        EmergencyContactController.EmergencyContactRequest userRequest = new EmergencyContactController.EmergencyContactRequest();
        userRequest.setName("Contact User");
        userRequest.setContactUserId(contactUser.getId());
        
        // First creation should succeed
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isCreated());
        
        // Second creation with same user should fail
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }
    
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Should enforce contact limit")
    public void testContactLimitEnforcement() throws Exception {
        // Set the maximum number of allowed contacts in service via reflection
        ReflectionTestUtils.setField(emergencyContactService, "maxEmergencyContacts", 3);
        
        // Create maximum allowed number of contacts
        for (int i = 0; i < 3; i++) {
            EmergencyContact contact = EmergencyContact.builder()
                    .user(testUser)
                    .name("Contact " + i)
                    .email("contact" + i + "@example.com")
                    .phone("+123456789" + i)
                    .relationship("Friend " + i)
                    .status(EmergencyContactStatus.ACTIVE) // Important: these are ACTIVE contacts
                    .notifySos(true)
                    .build();
            
            emergencyContactRepository.save(contact);
        }
        
        // Attempt to create one more contact
        EmergencyContactController.EmergencyContactRequest request = new EmergencyContactController.EmergencyContactRequest();
        request.setName("One Too Many");
        request.setEmail("toomany@example.com");
        request.setPhone("+9876543210");
        request.setRelationship("Friend");
        request.setNotifySos(true);
        
        // Should fail due to limit
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Maximum number of emergency contacts")));
        
        // Verify we still have only the maximum allowed number
        List<EmergencyContact> contacts = emergencyContactRepository.findActiveContactsByUserId(testUser.getId());
        assertEquals(3, contacts.size());
        
        // Remove one contact
        EmergencyContact contactToRemove = contacts.get(0);
        mockMvc.perform(delete("/api/emergency-contacts/{contactId}", contactToRemove.getId())
                .with(csrf()))
                .andExpect(status().isNoContent());
        
        // Now adding one more should succeed
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}", testUser.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        
        // Verify we're back at the limit
        contacts = emergencyContactRepository.findActiveContactsByUserId(testUser.getId());
        assertEquals(3, contacts.size());
    }
}

