package com.agents.api.dto;

/**
 * Request body for POST /api/ping endpoint (D-01).
 * Single-field record for user question.
 * No Lombok @Data (Java 21 records are concise and immutable).
 * No JPA @Entity (project has no database per PROJECT.md).
 */
public record PingRequest(String question) {}