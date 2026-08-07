package com.agents.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 0 unit test for Spring AI retry auto-configuration (LLM-05).
 *
 * <p>Asserts that {@code SpringAiRetryAutoConfiguration} provides a {@link RetryTemplate}
 * bean. In Spring Framework 7 (used by Spring Boot 4.1), {@code RetryTemplate} moved from
 * the external {@code spring-retry} library to {@code org.springframework.core.retry} -
 * this test imports the Spring core version to match the auto-configuration's bean type.
 *
 * <p>The detailed retry-count assertion (max-attempts=3 + 2s/4s/8s backoff on 429) is
 * verified in Plan 02's {@code PingControllerTest#shouldRetryOn429AndEmitErrorEvent}
 * via {@code verify(..., atMost(3))} - that test is the load-bearing assertion for LLM-06.
 * This test only verifies the bean exists.
 *
 * <p>If {@code spring.ai.retry} is misconfigured (e.g., flat {@code backoff-interval}
 * form that gets silently ignored - Pitfall #6), this test still passes (bean exists)
 * but the Plan 02 retry-count test will fail. The combination of the two tests gives
 * full coverage.
 */
@SpringBootTest
class RetryConfigTest {

    @Autowired(required = false)
    RetryTemplate retryTemplate;

    @Test
    void shouldApplyRetryConfig() {
        // LLM-05: spring.ai.retry configures a RetryTemplate bean
        assertThat(retryTemplate)
            .as("RetryTemplate should be auto-configured by SpringAiRetryAutoConfiguration")
            .isNotNull();
        // Detailed retry-count assertion is in Plan 02 PingControllerTest (D-07).
    }
}
