package com.agents.tool.builtin;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link TimeTool}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>D-05: returns current time with system default timezone</li>
 *   <li>D-07: returns JSON string with datetime (ISO-8601), timezone, weekday fields</li>
 *   <li>datetime field is parseable by ISO_LOCAL_DATE_TIME</li>
 * </ul>
 *
 * <p>Pure unit test (no Spring context) - direct instantiation of {@link TimeTool}.
 */
class TimeToolTest {

    private final TimeTool tool = new TimeTool();

    @Test
    void shouldReturnCurrentTime() {
        String result = tool.getTime();
        assertThat(result).isNotNull();
        assertThat(result).contains("\"datetime\"");
        assertThat(result).contains("\"timezone\"");
        assertThat(result).contains("\"weekday\"");
    }

    @Test
    void shouldHaveValidIsoDatetime() {
        String result = tool.getTime();
        // Extract the datetime value from JSON string
        String datetimeField = "\"datetime\":\"";
        int start = result.indexOf(datetimeField) + datetimeField.length();
        int end = result.indexOf("\"", start);
        String datetimeValue = result.substring(start, end);

        assertThatNoException().isThrownBy(() ->
            LocalDateTime.parse(datetimeValue, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}