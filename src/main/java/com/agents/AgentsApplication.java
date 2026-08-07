package com.agents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Phase 1 skeleton entry point for the Agent design pattern teaching library.
 *
 * <p>The Spring Boot standard application annotation already composes component scan,
 * auto-configuration, and boot configuration. No need to declare them explicitly. No
 * {@code @MapperScan} or {@code @EnableJpaRepositories} - the project has no database
 * (PROJECT.md Out of Scope).
 */
@SpringBootApplication
public class AgentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentsApplication.class, args);
    }
}
