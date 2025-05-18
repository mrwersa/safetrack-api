package com.safetrack.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safetrack.api.model.EmergencyContact;
import com.safetrack.api.model.EmergencyContactStatus;
import com.safetrack.api.model.User;
import com.safetrack.api.service.EmergencyContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the EmergencyContactController class.
 * Tests REST endpoints for emergency contact management.
 */
@WebMvcTest(EmergencyContactController.class)
public class EmergencyContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmergencyContactService emergencyContactService;

    // Test data
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONTACT_ID = UUID.randomUUID();
    private static final UUID CONTACT_USER_ID = UUID.randomUUID();
    private static final String CONTACT_NAME = "Emergency Contact";
    private static final String CONTACT_EMAIL = "emergency@example.com";
    private static final String CONTACT_PHONE = "+1234567890";
    private static final String VERIFICATION_TOKEN = "test-token-123";

    private User testUser;
    private EmergencyContact testContact;
    private EmergencyContactController.EmergencyContactRequest validRequest;
    private EmergencyContactController.UpdateEmergencyContactRequest updateRequest;

    @BeforeEach
    void setUp() {
        // Set up test user
        testUser = User.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .enabled(true)
                .accountNonLocked(true)
                .roles(new HashSet<>(Arrays.asList("ROLE_USER")))
                .build();

        // Set up test emergency contact
        testContact = EmergencyContact.builder()
                .id(CONTACT_ID)
                .user(testUser)
                .name(CONTACT_NAME)
                .email(CONTACT_EMAIL)
                .phone(CONTACT_PHONE)
                .relationship("Friend")
                .status(EmergencyContactStatus.PENDING)
                .verificationToken(VERIFICATION_TOKEN)
                .tokenCreatedAt(LocalDateTime.now().minusDays(1))
                .notifySos(true)
                .notifyGeofence(false)
                .notifyInactivity(false)
                .notifyLowBattery(false)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        // Set up valid request
        validRequest = EmergencyContactController.EmergencyContactRequest.builder()
                .name(CONTACT_NAME)
                .email(CONTACT_EMAIL)
                .phone(CONTACT_PHONE)
                .relationship("Friend")
                .notifySos(true)
                .notifyGeofence(false)
                .notifyInactivity(false)
                .notifyLowBattery(false)
                .build();

        // Set up update request
        updateRequest = new EmergencyContactController.UpdateEmergencyContactRequest();
        updateRequest.setName("Updated Name");
        updateRequest.setPhone("555-123-4567");
        updateRequest.setRelationship("Family");
        updateRequest.setNotifySos(false);
        updateRequest.setNotifyGeofence(true);
        updateRequest.setNotifyInactivity(true);
        updateRequest.setNotifyLowBattery(false);
        updateRequest.setNotes("Updated notes");
    }

    @Test
    @WithMockUser
    @DisplayName("Should get user's emergency contacts successfully")
    void testGetUserEmergencyContacts() throws Exception {
        // Create page of contacts
        List<EmergencyContact> contacts = Collections.singletonList(testContact);
        Page<EmergencyContact> contactPage = new PageImpl<>(contacts);
        
        // Mock service
        when(emergencyContactService.getUserEmergencyContacts(eq(USER_ID), any(Pageable.class)))
                .thenReturn(contactPage);

        // Execute and verify
        mockMvc.perform(get("/api/emergency-contacts/users/{userId}", USER_ID)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(CONTACT_ID.toString())))
                .andExpect(jsonPath("$.content[0].name", is(CONTACT_NAME)))
                .andExpect(jsonPath("$.content[0].email", is(CONTACT_EMAIL)))
                .andExpect(jsonPath("$.content[0].status", is(EmergencyContactStatus.PENDING.name())));
                
        // Verify service called
        verify(emergencyContactService).getUserEmergencyContacts(eq(USER_ID), any(Pageable.class));
    }

    @Test
    @WithMockUser
    @DisplayName("Should get active emergency contacts successfully")
    void testGetActiveEmergencyContacts() throws Exception {
        // Create list of active contacts
        EmergencyContact activeContact = EmergencyContact.builder()
                .id(CONTACT_ID)
                .user(testUser)
                .name(CONTACT_NAME)
                .email(CONTACT_EMAIL)
                .status(EmergencyContactStatus.ACTIVE)
                .build();
                
        List<EmergencyContact> activeContacts = Collections.singletonList(activeContact);
        
        // Mock service
        when(emergencyContactService.getActiveEmergencyContacts(USER_ID))
                .thenReturn(activeContacts);

        // Execute and verify
        mockMvc.perform(get("/api/emergency-contacts/users/{userId}/active", USER_ID)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(CONTACT_ID.toString())))
                .andExpect(jsonPath("$[0].name", is(CONTACT_NAME)))
                .andExpect(jsonPath("$[0].status", is(EmergencyContactStatus.ACTIVE.name())));
                
        // Verify service called
        verify(emergencyContactService).getActiveEmergencyContacts(USER_ID);
    }

    @Test
    @WithMockUser
    @DisplayName("Should get specific emergency contact successfully")
    void testGetEmergencyContact() throws Exception {
        // Mock service
        when(emergencyContactService.getEmergencyContact(CONTACT_ID))
                .thenReturn(testContact);

        // Execute and verify
        mockMvc.perform(get("/api/emergency-contacts/{contactId}", CONTACT_ID)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(CONTACT_ID.toString())))
                .andExpect(jsonPath("$.name", is(CONTACT_NAME)))
                .andExpect(jsonPath("$.email", is(CONTACT_EMAIL)))
                .andExpect(jsonPath("$.status", is(EmergencyContactStatus.PENDING.name())));
                
        // Verify service called
        verify(emergencyContactService).getEmergencyContact(CONTACT_ID);
    }

    @Test
    @WithMockUser
    @DisplayName("Should add emergency contact successfully")
    void testAddEmergencyContact() throws Exception {
        // Mock service
        when(emergencyContactService.addEmergencyContact(
                eq(USER_ID), anyString(), anyString(), anyString(), 
                anyString(), any(), anyBoolean(), anyBoolean(), 
                anyBoolean(), anyBoolean(), anyString()))
                .thenReturn(testContact);

        // Execute and verify
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}", USER_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(CONTACT_ID.toString())))
                .andExpect(jsonPath("$.name", is(CONTACT_NAME)))
                .andExpect(jsonPath("$.email", is(CONTACT_EMAIL)))
                .andExpect(jsonPath("$.status", is(EmergencyContactStatus.PENDING.name())));
                
        // Verify service called with correct parameters
        verify(emergencyContactService).addEmergencyContact(
                eq(USER_ID), 
                eq(CONTACT_NAME), 
                eq(CONTACT_EMAIL), 
                eq(CONTACT_PHONE), 
                eq("Friend"), 
                isNull(), 
                eq(true), 
                eq(false), 
                eq(false), 
                eq(false), 
                isNull());
    }

    @Test
    @WithMockUser
    @DisplayName("Should validate emergency contact request")
    void testAddEmergencyContact_ValidationError() throws Exception {
        // Create invalid request (missing required fields)
        EmergencyContactController.EmergencyContactRequest invalidRequest = new EmergencyContactController.EmergencyContactRequest();

        // Execute and verify validation failure
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}", USER_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
                
        // Verify service not called
        verify(emergencyContactService, never()).addEmergencyContact(
                any(), any(), any(), any(), any(), any(), anyBoolean(), 
                anyBoolean(), anyBoolean(), anyBoolean(), any());
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should handle user not found error")
    void testAddEmergencyContact_UserNotFound() throws Exception {
        // Mock service to throw exception
        when(emergencyContactService.addEmergencyContact(
                eq(USER_ID), anyString(), anyString(), anyString(), 
                anyString(), any(), anyBoolean(), anyBoolean(), 
                anyBoolean(), anyBoolean(), anyString()))
                .thenThrow(new IllegalArgumentException("User not found with ID: " + USER_ID));

        // Execute and verify error handling
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}", USER_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("User not found")));
    }

    @Test
    @WithMockUser
    @DisplayName("Should update emergency contact successfully")
    void testUpdateEmergencyContact() throws Exception {
        // Create updated contact
        EmergencyContact updatedContact = EmergencyContact.builder()
                .id(CONTACT_ID)
                .user(testUser)
                .name("Updated Name")
                .phone("555-123-4567")
                .relationship("Family")
                .status(EmergencyContactStatus.ACTIVE)
                .notifySos(false)
                .notifyGeofence(true)
                .notifyInactivity(true)
                .notifyLowBattery(false)
                .notes("Updated notes")
                .build();
        
        // Mock service
        when(emergencyContactService.updateEmergencyContact(
                eq(CONTACT_ID), anyString(), anyString(), anyString(), 
                any(), any(), any(), any(), anyString()))
                .thenReturn(updatedContact);

        // Execute and verify
        mockMvc.perform(put("/api/emergency-contacts/{contactId}", CONTACT_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(CONTACT_ID.toString())))
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.relationship", is("Family")))
                .andExpect(jsonPath("$.notifySos", is(false)))
                .andExpect(jsonPath("$.notifyGeofence", is(true)));
                
        // Verify service called with correct parameters
        verify(emergencyContactService).updateEmergencyContact(
                eq(CONTACT_ID), 
                eq("Updated Name"), 
                eq("555-123-4567"), 
                eq("Family"), 
                eq(false), 
                eq(true), 
                eq(true), 
                eq(false), 
                eq("Updated notes"));
    }

    @Test
    @WithMockUser
    @DisplayName("Should remove emergency contact successfully")
    void testRemoveEmergencyContact() throws Exception {
        // Mock service (void method)
        doNothing().when(emergencyContactService).removeEmergencyContact(CONTACT_ID);

        // Execute and verify
        mockMvc.perform(delete("/api/emergency-contacts/{contactId}", CONTACT_ID)
                .with(csrf()))
                .andExpect(status().isNoContent());
                
        // Verify service called
        verify(emergencyContactService).removeEmergencyContact(CONTACT_ID);
    }

    @Test
    @DisplayName("Should verify contact successfully - public endpoint")
    void testVerifyContact() throws Exception {
        // Create active contact for response
        EmergencyContact activeContact = EmergencyContact.builder()
                .id(CONTACT_ID)
                .user(testUser)
                .name(CONTACT_NAME)
                .email(CONTACT_EMAIL)
                .status(EmergencyContactStatus.ACTIVE)
                .acceptedAt(LocalDateTime.now())
                .build();
        
        // Mock service
        when(emergencyContactService.verifyContact(VERIFICATION_TOKEN))
                .thenReturn(activeContact);

        // Execute and verify - note: no authentication needed for this endpoint
        mockMvc.perform(post("/api/emergency-contacts/verify/{token}", VERIFICATION_TOKEN)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactId", is(CONTACT_ID.toString())))
                .andExpect(jsonPath("$.username", is(testUser.getUsername())))
                .andExpect(jsonPath("$.contactName", is(CONTACT_NAME)))
                .andExpect(jsonPath("$.message", containsString("verified and activated successfully")));
                
        // Verify service called
        verify(emergencyContactService).verifyContact(VERIFICATION_TOKEN);
    }
    
    @Test
    @DisplayName("Should handle invalid verification token")
    void testVerifyContact_InvalidToken() throws Exception {
        // Mock service to throw exception
        when(emergencyContactService.verifyContact("invalid-token"))
                .thenThrow(new IllegalArgumentException("Invalid or expired verification token"));

        // Execute and verify error handling - note: no authentication needed
        mockMvc.perform(post("/api/emergency-contacts/verify/{token}", "invalid-token")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid or expired verification token")));
    }
    
    @Test
    @DisplayName("Should decline contact successfully - public endpoint")
    void testDeclineContact() throws Exception {
        // Create declined contact
        EmergencyContact declinedContact = EmergencyContact.builder()
                .id(CONTACT_ID)
                .user(testUser)
                .name(CONTACT_NAME)
                .email(CONTACT_EMAIL)
                .status(EmergencyContactStatus.DECLINED)
                .build();
        
        // Mock service
        when(emergencyContactService.declineContact(VERIFICATION_TOKEN))
                .thenReturn(declinedContact);

        // Execute and verify - note: no authentication needed
        mockMvc.perform(post("/api/emergency-contacts/decline/{token}", VERIFICATION_TOKEN)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactId", is(CONTACT_ID.toString())))
                .andExpect(jsonPath("$.username", is(testUser.getUsername())))
                .andExpect(jsonPath("$.contactName", is(CONTACT_NAME)))
                .andExpect(jsonPath("$.message", containsString("declined successfully")));
                
        // Verify service called
        verify(emergencyContactService).declineContact(VERIFICATION_TOKEN);
    }

    @Test
    @WithMockUser
    @DisplayName("Should resend verification successfully")
    void testResendVerification() throws Exception {
        // Mock service
        when(emergencyContactService.resendVerification(CONTACT_ID))
                .thenReturn(testContact);

        // Execute and verify
        mockMvc.perform(post("/api/emergency-contacts/{contactId}/resend", CONTACT_ID)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("Verification email resent successfully")));
                
        // Verify service called
        verify(emergencyContactService).resendVerification(CONTACT_ID);
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should handle errors when resending verification")
    void testResendVerification_Error() throws Exception {
        // Mock service to throw exception
        when(emergencyContactService.resendVerification(CONTACT_ID))
                .thenThrow(new IllegalArgumentException("Can only resend verification for pending contacts"));

        // Execute and verify error handling
        mockMvc.perform(post("/api/emergency-contacts/{contactId}/resend", CONTACT_ID)
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Can only resend verification for pending contacts")));
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should get pending contacts information")
    void testGetPendingContacts() throws Exception {
        // Create pending info response
        Map<String, Integer> pendingInfo = new HashMap<>();
        pendingInfo.put("pendingSent", 2);
        pendingInfo.put("pendingReceived", 1);
        
        // Mock service
        when(emergencyContactService.checkPendingContacts(USER_ID))
                .thenReturn(pendingInfo);

        // Execute and verify
        mockMvc.perform(get("/api/emergency-contacts/users/{userId}/pending", USER_ID)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingSent", is(2)))
                .andExpect(jsonPath("$.pendingReceived", is(1)));
                
        // Verify service called
        verify(emergencyContactService).checkPendingContacts(USER_ID);
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should send emergency notifications successfully")
    void testSendEmergencyNotifications() throws Exception {
        // Create emergency notification request
        EmergencyContactController.EmergencyNotificationRequest notificationRequest = 
                new EmergencyContactController.EmergencyNotificationRequest();
        notificationRequest.setLatitude(40.7128);
        notificationRequest.setLongitude(-74.0060);
        notificationRequest.setMessage("Help! Emergency!");
        
        // Mock service
        when(emergencyContactService.sendEmergencyNotifications(
                eq(USER_ID), anyDouble(), anyDouble(), anyString()))
                .thenReturn(3); // 3 contacts notified

        // Execute and verify
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}/notify-emergency", USER_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(notificationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifiedCount", is(3)))
                .andExpect(jsonPath("$.message", containsString("Emergency notifications sent to 3 contacts")));
                
        // Verify service called with correct parameters
        verify(emergencyContactService).sendEmergencyNotifications(
                eq(USER_ID), 
                eq(40.7128), 
                eq(-74.0060), 
                eq("Help! Emergency!"));
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should validate emergency notification request")
    void testSendEmergencyNotifications_ValidationError() throws Exception {
        // Create invalid request (missing required fields)
        EmergencyContactController.EmergencyNotificationRequest invalidRequest = 
                new EmergencyContactController.EmergencyNotificationRequest();

        // Execute and verify validation failure
        mockMvc.perform(post("/api/emergency-contacts/users/{userId}/notify-emergency", USER_ID)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
                
        // Verify service not called
        verify(emergencyContactService, never()).sendEmergencyNotifications(
                any(), anyDouble(), anyDouble(), any());
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should get users designating as contact")
    void testGetUsersDesignatingAsContact() throws Exception {
        // Create a contact where test user is designated as emergency contact
        User designatingUser = User.builder()
                .id(UUID.randomUUID())
                .username("designator")
                .build();
                
        EmergencyContact designatingContact = EmergencyContact.builder()
                .id(UUID.randomUUID())
                .user(designatingUser)
                .contactUser(testUser)
                .name(testUser.getUsername())
                .status(EmergencyContactStatus.ACTIVE)
                .build();
                
        List<EmergencyContact> designatingContacts = Collections.singletonList(designatingContact);
        Page<EmergencyContact> contactPage = new PageImpl<>(designatingContacts);
        
        // Mock service
        when(emergencyContactService.getUsersDesignatingAsContact(eq(USER_ID), any(Pageable.class)))
                .thenReturn(contactPage);

        // Execute and verify
        mockMvc.perform(get("/api/emergency-contacts/users/{userId}/designated-by", USER_ID)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId", is(designatingUser.getId().toString())))
                .andExpect(jsonPath("$.content[0].contactUserId", is(USER_ID.toString())));
                
        // Verify service called
        verify(emergencyContactService).getUsersDesignatingAsContact(eq(USER_ID), any(Pageable.class));
    }
    
    @Test
    @DisplayName("Should reject unauthorized access to protected endpoints")
    void testUnauthorizedAccess() throws Exception {
        // Try to access a protected endpoint without authentication
        mockMvc.perform(get("/api/emergency-contacts/users/{userId}", USER_ID))
                .andExpect(status().isUnauthorized());
                
        // Verify service not called
        verify(emergencyContactService, never()).getUserEmergencyContacts(any(), any(Pageable.class));
    }
    
    @Test
    @WithMockUser
    @DisplayName("Should handle access denied errors")
    void testAccessDeniedError() throws Exception {
        // Mock service to throw AccessDeniedException
        when(emergencyContactService.getUserEmergencyContacts(eq(USER_ID), any(Pageable.class)))
                .thenThrow(new AccessDeniedException("Not authorized to access this user's data"));

        // Execute and verify proper error handling
        mockMvc.perform(get("/api/emergency-contacts/users/{userId}", USER_ID)
                .with(csrf()))
                .andExpect(status().isForbidden());
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Should allow admin access to any user's data")
    void testAdminAccess() throws Exception {
        // Create page of contacts
        List<EmergencyContact> contacts = Collections.singletonList(testContact);
        Page<EmergencyContact> contactPage = new PageImpl<>(contacts);
        
        // Mock service for admin access
        when(emergencyContactService.getUserEmergencyContacts(eq(USER_ID), any(Pageable.class)))
                .thenReturn(contactPage);

        // Execute and verify admin can access any user's data
        mockMvc.perform(get("/api/emergency-contacts/users/{userId}", USER_ID)
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
                
        // Verify service called
        verify(emergencyContactService).getUserEmergencyContacts(eq(USER_ID), any(Pageable.class));
    }
}

