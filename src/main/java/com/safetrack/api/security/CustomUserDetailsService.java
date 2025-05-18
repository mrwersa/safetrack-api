package com.safetrack.api.security;

import com.safetrack.api.model.User;
import com.safetrack.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom implementation of Spring Security's UserDetailsService.
 * This service loads user-specific data from the database for authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by username from the database.
     * This method is used by Spring Security during authentication.
     *
     * @param username The username to look up
     * @return UserDetails object representing the found user
     * @throws UsernameNotFoundException if the user is not found
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("User not found with username: {}", username);
                    return new UsernameNotFoundException("User not found with username: " + username);
                });
        
        return convertToUserDetails(user);
    }

    /**
     * Converts our custom User entity to Spring Security's UserDetails.
     * Maps user's roles to granted authorities and handles account status flags.
     *
     * @param user The User entity to convert
     * @return UserDetails implementation representing the user
     */
    private UserDetails convertToUserDetails(User user) {
        // Convert roles to SimpleGrantedAuthority objects
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role))
                .collect(Collectors.toList());

        // Map our User entity to Spring Security's UserDetails
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getEnabled(),              // enabled
                true,                          // accountNonExpired - we don't have this field, so default to true
                true,                          // credentialsNonExpired - we don't have this field, so default to true
                user.getAccountNonLocked(),     // accountNonLocked
                authorities                    // authorities from roles
        );
    }
}

