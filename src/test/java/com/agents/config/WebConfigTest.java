package com.agents.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

/**
 * Wave 0 integration test for {@link WebConfig} CORS configuration (ARCH-09, T-1-CORS).
 *
 * <p>Sends an OPTIONS preflight request with the Vite dev server origin and asserts the
 * response carries the matching CORS allow-origin header. The exact-origin assertion
 * catches the wildcard-origin security anti-pattern (any website could call the API).
 *
 * <p>Spring Boot 4.1 removed {@code @AutoConfigureWebTestClient} from the test
 * auto-configure module, so we manually build a {@link WebTestClient} bound to the
 * {@code RANDOM_PORT} Tomcat server via {@code @LocalServerPort}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebConfigTest {

    @LocalServerPort
    int port;

    @Test
    void shouldAllowCorsForLocalhost5173() {
        // ARCH-09 / T-1-CORS: explicit origin allowed, not a wildcard
        WebTestClient client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .responseTimeout(Duration.ofSeconds(10))
            .build();

        client.options().uri("/api/ping")
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST")
            .exchange()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
    }
}
