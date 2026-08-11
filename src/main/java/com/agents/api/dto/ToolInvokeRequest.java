package com.agents.api.dto;

import java.util.Map;

/**
 * Request body for {@code POST /api/tools/{toolName}/invoke}.
 *
 * <p>请求体结构: {@code {"arguments":{"city":"北京"}}}。Controller 从 record 中取出
 * {@code arguments} Map 并序列化为平铺 JSON {@code {"city":"北京"}} 传给
 * {@code ToolCallback.call(String)} (Pitfall 6 - 避免传入嵌套的 {@code {"arguments":...}} 结构)。
 *
 * <p>D-06: RESTful 风格端点 DTO，供 ToolController (Plan 03) 反序列化请求。
 *
 * <p>反模式规避: 不加 Lombok {@code @Data} / {@code @Builder} - Java 21 record 已足够；
 * 不加 JPA {@code @Entity} - PROJECT.md Out of Scope（无 DB）。
 */
public record ToolInvokeRequest(Map<String, Object> arguments) {}