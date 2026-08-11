package com.agents.tool.builtin;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 当前时间查询工具（系统默认时区）。
 *
 * <p>D-05: 固定系统默认时区，不传参。教学演示用，简单够用。
 * 如需时区转换可在后续扩展。
 *
 * <p>D-07: 返回结构化 JSON 字符串（String），便于 LLM 解析。
 * 工具名为简短英文 {@code time}。
 */
@Component
public class TimeTool {

    private static final DateTimeFormatter ISO_FORMATTER =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Tool(name = "time",
          description = "获取当前日期和时间（系统默认时区）。返回 JSON 字符串，含 datetime (ISO-8601), timezone, weekday 字段。无参数。")
    public String getTime() {
        // D-05: 固定系统默认时区，不传参
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.now(zone);
        String weekday = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        return String.format(
            "{\"datetime\":\"%s\",\"timezone\":\"%s\",\"weekday\":\"%s\"}",
            now.format(ISO_FORMATTER),
            zone.getId(),
            weekday
        );
    }
}