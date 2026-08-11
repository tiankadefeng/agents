package com.agents.api.dto;

/**
 * Tool metadata returned by {@code GET /api/tools}.
 *
 * <p>Fields sourced from {@code ToolDefinition.name()} / {@code ToolDefinition.description()} /
 * {@code ToolDefinition.inputSchema()} (Spring AI 2.0 API). {@code inputSchema} is a JSON
 * schema string (e.g., {@code {"type":"object","properties":{"city":{"type":"string"}}}}) -
 * frontend can {@code JSON.parse()} if needed.
 *
 * <p>D-06: RESTful 风格端点 DTO，供 ToolController (Plan 03) 序列化返回。
 *
 * <p>反模式规避: 不加 Lombok {@code @Data} - Java 21 record 已足够；
 * 不加 JPA {@code @Entity} - PROJECT.md Out of Scope（无 DB）。
 */
public record ToolInfo(String name, String description, String inputSchema) {}