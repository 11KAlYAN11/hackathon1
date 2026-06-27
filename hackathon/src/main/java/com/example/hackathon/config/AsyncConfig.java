package com.example.hackathon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // @Async on ReplanningService.onAgentOffline uses Spring's default thread pool
    // Keeps PATCH /agents/{id}/status non-blocking
}
