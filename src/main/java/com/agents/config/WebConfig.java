package com.agents.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for the teaching frontend.
 *
 * <p>Allows the Vite dev server origin (port 5173) to call {@code /api/**} endpoints.
 * Explicit origin (not a wildcard) - combining a wildcard origin with credentials-true
 * is a known security anti-pattern. Teaching scenario uses credentials-false (no
 * cookies), but we still pin the origin so that any future cookie-enabled iteration
 * stays safe.
 *
 * <p>Do NOT write a custom {@code Filter} to set CORS headers manually - the
 * {@code addCorsMappings} override handles OPTIONS preflight, header injection, and
 * origin matching.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("POST", "GET", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(false);
    }
}
