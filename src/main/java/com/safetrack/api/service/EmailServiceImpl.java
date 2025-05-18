package com.safetrack.api.service;

import com.safetrack.api.model.EmergencyContact;
import com.safetrack.api.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the EmailService interface that handles email operations
 * for the SafeTrack application using Spring JavaMailSender and Thymeleaf templates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender emailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${safetrack.baseUrl}")
    private String baseUrl;

    @Value("${safetrack.email.templates.location:classpath:email-templates/}")
    private String templateLocation;

    /**
     * Sends an email with the given subject and content to the recipient.
     *
     * @param to The recipient's email address
     * @param subject The email subject
     * @param content The email content (HTML)
     * @return CompletableFuture representing the asynchronous operation
     */
    @Async
    @Retryable(
        value = { MessagingException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public CompletableFuture<Void> sendEmail(String to, String subject, String content) {
        try {
            log.debug("Preparing to send email to {} with subject: {}", to, subject);
            
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            
            emailSender.send(message);
            log.info("Email sent successfully to {} with subject: {}", to, subject);
            
            return CompletableFuture.completedFuture(null);
        } catch (MessagingException e) {
            log.error("Failed to send email to {} with subject: {}", to, subject, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Sends an email with template support, replacing placeholders in the template.
     *
     * @param to The recipient's email address
     * @param subject The email subject
     * @param templateName The name of the template to use
     * @param templateVariables Variables to replace in the template
     * @return CompletableFuture representing the asynchronous operation
     */
    @Async
    @Retryable(
        value = { MessagingException.class, IOException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Override
    public CompletableFuture<Void> sendTemplatedEmail(String to, String subject, String templateName, Map<String, Object> templateVariables) {
        try {
            log.debug("Preparing to send templated email to {} with template: {}", to, templateName);
            
            Context context = new Context();
            templateVariables.put("baseUrl", baseUrl);
            context.setVariables(templateVariables);
            
            String content;
            try {
                // Try to use Thymeleaf template engine first
                content = templateEngine.process(templateName, context);
            } catch (Exception e) {
                log.warn("Could not process template with Thymeleaf engine, falling back to simple template replacement", e);
                content = loadTemplateFromFile(templateName);
                content = replaceTemplateVariables(content, templateVariables);
            }
            
            return sendEmail(to, subject, content);
        } catch (IOException e) {
            log.error("Failed to load template: {}", templateName, e);
            throw new RuntimeException("Failed to load email template", e);
        }
    }
    
    /**
     * Loads a template from a file.
     *
     * @param templateName The name of the template to load
     * @return The template content as a string
     * @throws IOException If the template file cannot be read
     */
    private String loadTemplateFromFile(String templateName) throws IOException {
        String templatePath = templateLocation;
        if (!templatePath.endsWith("/")) {
            templatePath += "/";
        }
        templatePath += templateName + ".html";
        
        ClassPathResource resource = new ClassPathResource(templatePath);
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }
    
    /**
     * Replaces placeholders in a template with actual values.
     *
     * @param template The template with placeholders
     * @param variables The variables to replace placeholders with
     * @return The processed template with replaced variables
     */
    private String replaceTemplateVariables(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "\\{\\{" + entry.getKey() + "\\}\\}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replaceAll(placeholder, value);
        }
        return result;
    }

    /**
     * Sends a verification email to a newly added emergency contact.
     *
     * @param emergencyContact The emergency contact to verify
     * @return CompletableFuture representing the asynchronous operation
     */
    @Override
    public CompletableFuture<Void> sendEmergencyContactVerificationEmail(EmergencyContact emergencyContact) {
        log.debug("Sending emergency contact verification email to: {}", emergencyContact.getEmail());
        
        if (emergencyContact.getEmail() == null || emergencyContact.getEmail().trim().isEmpty()) {
            log.warn("Cannot send verification email - contact has no email address");
            return CompletableFuture.completedFuture(null);
        }
        
        if (emergencyContact.getVerificationToken() == null) {
            log.warn("Cannot send verification email - contact has no verification token");
            return CompletableFuture.completedFuture(null);
        }
        
        User user = emergencyContact.getUser();
        
        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("contactName", emergencyContact.getName());
        templateVariables.put("userName", getFullUserName(user));
        templateVariables.put("verificationLink", baseUrl + "/verify-contact?token=" + emergencyContact.getVerificationToken());
        templateVariables.put("declineLink", baseUrl + "/decline-contact?token=" + emergencyContact.getVerificationToken());
        
        return sendTemplatedEmail(
            emergencyContact.getEmail(),
            "Emergency Contact Verification - SafeTrack",
            "emergency-contact-verification",
            templateVariables
        );
    }

    /**
     * Sends a notification that the emergency contact has been verified.
     *
     * @param user The user who added the emergency contact
     * @param emergencyContact The emergency contact that was verified
     * @return CompletableFuture representing the asynchronous operation
     */
    @Override
    public CompletableFuture<Void> sendEmergencyContactVerifiedEmail(User user, EmergencyContact emergencyContact) {
        log.debug("Sending emergency contact verified email to: {}", user.getEmail());
        
        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("userName", getFullUserName(user));
        templateVariables.put("contactName", emergencyContact.getName());
        
        return sendTemplatedEmail(
            user.getEmail(),
            "Emergency Contact Verified - SafeTrack",
            "emergency-contact-verified",
            templateVariables
        );
    }

    /**
     * Sends a notification that an emergency contact declined the invitation.
     *
     * @param user The user who added the emergency contact
     * @param emergencyContact The emergency contact that declined
     * @return CompletableFuture representing the asynchronous operation
     */
    @Override
    public CompletableFuture<Void> sendEmergencyContactDeclinedEmail(User user, EmergencyContact emergencyContact) {
        log.debug("Sending emergency contact declined email to: {}", user.getEmail());
        
        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("userName", getFullUserName(user));
        templateVariables.put("contactName", emergencyContact.getName());
        
        return sendTemplatedEmail(
            user.getEmail(),
            "Emergency Contact Declined - SafeTrack",
            "emergency-contact-declined",
            templateVariables
        );
    }

    /**
     * Sends an emergency alert to a contact when an SOS is triggered.
     *
     * @param emergencyContact The emergency contact to notify
     * @param location Optional location information (e.g., coordinates or address)
     * @param message Optional custom message from the user
     * @return CompletableFuture representing the asynchronous operation
     */
    @Override
    public CompletableFuture<Void> sendEmergencyAlertEmail(EmergencyContact emergencyContact, String location, String message) {
        log.debug("Sending emergency alert email to: {}", emergencyContact.getEmail());
        
        if (emergencyContact.getEmail() == null || emergencyContact.getEmail().trim().isEmpty()) {
            log.warn("Cannot send emergency alert - contact has no email address");
            return CompletableFuture.completedFuture(null);
        }
        
        User user = emergencyContact.getUser();
        
        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("contactName", emergencyContact.getName());
        templateVariables.put("userName", getFullUserName(user));
        templateVariables.put("userFirstName", user.getFirstName());
        templateVariables.put("location", location != null ? location : "Unknown location");
        templateVariables.put("message", message != null && !message.trim().isEmpty() ? message : "No message provided");
        templateVariables.put("timestamp", java.time.LocalDateTime.now().toString());
        templateVariables.put("mapLink", location != null ? "https://maps.google.com/?q=" + location.replace(" ", "+") : "#");
        
        return sendTemplatedEmail(
            emergencyContact.getEmail(),
            "EMERGENCY ALERT: " + user.getFirstName() + " needs help! - SafeTrack",
            "emergency-alert",
            templateVariables
        );
    }

    /**
     * Sends a geofence alert to a contact when a user leaves a defined safe area.
     *
     * @param emergencyContact The emergency contact to notify
     * @param location The location where the user left the safe area
     * @param geofenceName The name of the geofence that was triggered
     * @return CompletableFuture representing the asynchronous operation
     */
    @Override
    public CompletableFuture<Void> sendGeofenceAlertEmail(EmergencyContact emergencyContact, String location, String geofenceName) {
        log.debug("Sending geofence alert email to: {}", emergencyContact.getEmail());
        
        if (emergencyContact.getEmail() == null || emergencyContact.getEmail().trim().isEmpty()) {
            log.warn("Cannot send geofence alert - contact has no email address");
            return CompletableFuture.completedFuture(null);
        }
        
        if (!emergencyContact.getNotifyGeofence()) {
            log.debug("Contact has geofence notifications disabled, not sending alert");
            return CompletableFuture.completedFuture(null);
        }
        
        User user = emergencyContact.getUser();
        
        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("contactName", emergencyContact.getName());
        templateVariables.put("userName", getFullUserName(user));
        templateVariables.put("userFirstName", user.getFirstName());
        templateVariables.put("location", location != null ? location : "Unknown location");
        templateVariables.put("geofenceName", geofenceName != null ? geofenceName : "a designated safe area");
        templateVariables.put("timestamp", java.time.LocalDateTime.now().toString());
        templateVariables.put("mapLink", location != null ? "https://maps.google.com/?q=" + location.replace(" ", "+") : "#");
        
        return sendTemplatedEmail(
            emergencyContact.getEmail(),
            "Geofence Alert: " + user.getFirstName() + " has left " + (geofenceName != null ? geofenceName : "a safe area") + " - SafeTrack",
            "geofence-alert",
            templateVariables
        );
    }

    /**
     * Sends an inactivity alert to a contact when a user hasn't been active for a defined period.
     *
     * @param emergencyContact The emergency contact to notify
     * @param lastSeenTime When the user was last seen
     * @return CompletableFuture representing the asynchronous operation
     */
    @Override
    public CompletableFuture<Void> sendInactivityAlertEmail(EmergencyContact emergencyContact, String lastSeenTime) {
        log.debug("Sending inactivity alert email to: {}", emergencyContact.getEmail());
        
        if (emergencyContact.getEmail() == null || emergencyContact.getEmail().trim().isEmpty()) {
            log.warn("Cannot send inactivity alert - contact has no email address");
            return CompletableFuture.completedFuture(null);
        }
        
        if (!emergencyContact.getNotifyInactivity()) {
            log.debug("Contact has inactivity notifications disabled, not sending alert");
            return CompletableFuture.completedFuture(null);
        }
        
        User user = emergencyContact.getUser();
        
        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("contactName", emergencyContact.getName());
        templateVariables.put("userName", getFullUserName(user));
        templateVariables.put("userFirstName", user.getFirstName());
        templateVariables.put("lastSeenTime", lastSeenTime != null ? lastSeenTime : "Unknown");
        templateVariables.put("timestamp", java.time.LocalDateTime.now().toString());
        
        return sendTemplatedEmail(
            emergencyContact.getEmail(),
            "Inactivity Alert: " + user.getFirstName() + " hasn't been active - SafeTrack",
            "inactivity-alert",
            templateVariables
        );
    }

    /**
     * Sends a low battery alert to a contact when a user's device battery is critically low.
     *
     * @param emergencyContact The emergency contact to notify
     * @param batteryPercentage The current battery percentage
     * @param location Optional last known location
     * @return CompletableFuture representing the asynchronous operation
     */
    @Override
    public CompletableFuture<Void> sendLowBatteryAlertEmail(EmergencyContact emergencyContact, int batteryPercentage, String location) {
        log.debug("Sending low battery alert email to: {}", emergencyContact.getEmail());
        
        if (emergencyContact.getEmail() == null || emergencyContact.getEmail().trim().isEmpty()) {
            log.warn("Cannot send low battery alert - contact has no email address");
            return CompletableFuture.completedFuture(null);
        }
        
        if (!emergencyContact.getNotifyLowBattery()) {
            log.debug("Contact has low battery notifications disabled, not sending alert");
            return CompletableFuture.completedFuture(null);
        }
        
        User user = emergencyContact.getUser();
        
        Map<String, Object> templateVariables = new HashMap<>();
        templateVariables.put("contactName", emergencyContact.getName());
        templateVariables.put("userName", getFullUserName(user));
        templateVariables.put("userFirstName", user.getFirstName());
        templateVariables.put("batteryPercentage", batteryPercentage);
        templateVariables.put("location", location != null ? location : "Unknown location");
        templateVariables.put("timestamp", java.time.LocalDateTime.now().toString());
        templateVariables.put("mapLink", location != null ? "https://maps.google.com/?q=" + location.replace(" ", "+") : "#");
        
        return sendTemplatedEmail(
            emergencyContact.getEmail(),
            "Low Battery Alert: " + user.getFirstName() + "'s device is at " + batteryPercentage + "% - SafeTrack",
            "low-battery-alert",
            templateVariables
        );
    }
    
    /**
     * Utility method to get a user's full name.
     * If first name and last name are available, returns them combined.
     * Otherwise, falls back to username or email.
     *
     * @param user The user to get the name for
     * @return The full name of the user
     */
    private String getFullUserName(User user) {
        if (user.getFirstName() != null && !user.getFirstName().trim().isEmpty()) {
            if (user.getLastName() != null && !user.getLastName().trim().isEmpty()) {
                return user.getFirstName() + " " + user.getLastName();
            }
            return user.getFirstName();
        }
        
        return user.getUsername() != null ? user.getUsername() : user.getEmail();
    }
}
