package com.safetrack.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Configuration for enabling asynchronous processing and retry functionality.
 * This is used primarily by the email service to send emails asynchronously
 * and handle retry logic for failed email attempts.
 */
@Configuration
@EnableAsync
@EnableRetry
public class AsyncConfig {
    // The actual configuration values are defined in application.properties
    // This class enables the async and retry functionality
}

