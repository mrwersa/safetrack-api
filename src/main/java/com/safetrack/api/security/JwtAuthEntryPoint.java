package com.safetrack.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom authentication entry point for handling unauthorized access attempts.
 * This component is called when an unauthenticated user tries to access a secured resource.
 * It returns a proper HTTP 401 Unauthorized response with a JSON error message.
 */
@Component
@Slf4j
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Called when an unauthenticated user tries to access a secured resource.
     * Returns a JSON response with error details and HTTP 401 status.
     *
     * @param request The HTTP request that resulted in an AuthenticationException
     * @param response The HTTP response to update
     * @param authException The exception that was thrown during authentication
     * @throws IOException If an I/O error occurs during writing to the response
     * @throws ServletException If a servlet error occurs
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        log.error("Unauthorized access error: {}", authException.getMessage());
        
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        
        // Create error response JSON
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        errorDetails.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        errorDetails.put("error", "Unauthorized");
        errorDetails.put("message", authException.getMessage() != null ? 
                authException.getMessage() : "Authentication required");
        errorDetails.put("path", request.getRequestURI());
        
        // Write JSON error response
        objectMapper.writeValue(response.getOutputStream(), errorDetails);
    }
}

