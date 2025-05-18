package com.safetrack.api.controller;

import com.safetrack.api.model.Role;
import com.safetrack.api.model.User;
import com.safetrack.api.repository.UserRepository;
import com.safetrack.api.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;

/**
 * Controller for handling authentication operations like registration and login.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication management API")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * User registration endpoint.
     * Registers a new user with basic role.
     *
     * @param request Registration details
     * @return Response with registration status
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account with USER role")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or username/email already in use"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<RegisterResponse> registerUser(@Valid @RequestBody RegisterRequest request) {
        log.info("Registering new user with username: {}", request.getUsername());
        
        // Check if username or email already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Registration failed: Username {} already taken", request.getUsername());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already taken");
        }
        
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: Email {} already in use", request.getEmail());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
        }
        
        // Create new user entity
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .enabled(true)
                .accountNonLocked(true)
                .roles(new HashSet<>())
                .build();
        
        // Add default USER role
        user.addRole(Role.USER.getAuthority());
        
        // Save user to database
        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(
                        savedUser.getId().toString(),
                        savedUser.getUsername(),
                        "User registered successfully"
                ));
    }

    /**
     * User login endpoint.
     * Authenticates a user and returns a JWT token.
     *
     * @param request Login credentials
     * @return Response with JWT token
     */
    @PostMapping("/login")
    @Operation(summary = "Authenticate a user", description = "Validates credentials and returns JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful", 
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Processing login request for user: {}", request.getUsername());
        
        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
            
            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Generate JWT token
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtService.generateToken(userDetails);
            
            log.info("User successfully authenticated: {}", request.getUsername());
            
            return ResponseEntity.ok(new LoginResponse(token, "Authentication successful"));
            
        } catch (Exception e) {
            log.error("Authentication failed for user {}: {}", request.getUsername(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
    }

    /**
     * Registration request payload.
     */
    @Data
    @Builder
    @AllArgsConstructor
    public static class RegisterRequest {
        
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        private String username;
        
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;
        
        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;
        
        @Size(max = 50, message = "First name must be less than 50 characters")
        private String firstName;
        
        @Size(max = 50, message = "Last name must be less than 50 characters")
        private String lastName;
    }

    /**
     * Registration response payload.
     */
    @Data
    @AllArgsConstructor
    public static class RegisterResponse {
        private String id;
        private String username;
        private String message;
    }

    /**
     * Login request payload.
     */
    @Data
    @AllArgsConstructor
    public static class LoginRequest {
        
        @NotBlank(message = "Username is required")
        private String username;
        
        @NotBlank(message = "Password is required")
        private String password;
    }

    /**
     * Login response payload.
     */
    @Data
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private String message;
    }
}

