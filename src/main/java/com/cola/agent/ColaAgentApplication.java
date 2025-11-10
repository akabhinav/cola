package com.cola.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * COLA - Coding Agent Platform
 *
 * Main application class for the AI-powered coding agent platform.
 * Provides intelligent code planning, generation, compilation, testing, and deployment.
 *
 * @author COLA Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ColaAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ColaAgentApplication.class, args);
    }
}
