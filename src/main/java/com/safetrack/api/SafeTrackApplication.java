package com.safetrack.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the SafeTrack API application.
 * This class bootstraps the Spring Boot application.
 */
@SpringBootApplication
public class SafeTrackApplication {

    /**
     * Main method that starts the Spring Boot application.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SafeTrackApplication.class, args);
    }
}

