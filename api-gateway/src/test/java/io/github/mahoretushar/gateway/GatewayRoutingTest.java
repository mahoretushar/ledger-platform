package io.github.mahoretushar.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Two throwaway HTTP servers stand in for the services, so the test checks real proxying. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    static final HttpServer ledger = backend("ledger");
    static final HttpServer notifications = backend("notifications");

    @LocalServerPort int port;

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("gateway.ledger-uri", () -> "http://localhost:" + ledger.getAddress().getPort());
        registry.add("gateway.notification-uri", () -> "http://localhost:" + notifications.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        ledger.stop(0);
        notifications.stop(0);
    }

    @Test
    void routesByPathAndPropagatesARequestId() {
        var client = RestClient.create("http://localhost:" + port);

        var accounts = client.get().uri("/api/v1/accounts/42").retrieve().toEntity(String.class);
        assertThat(accounts.getBody()).startsWith("ledger:/api/v1/accounts/42");
        String generated = accounts.getHeaders().getFirst("X-Request-Id");
        assertThat(generated).isNotBlank();
        assertThat(accounts.getBody()).endsWith("rid=" + generated);

        var notes = client.get().uri("/api/v1/notifications").header("X-Request-Id", "trace-123")
                .retrieve().toEntity(String.class);
        assertThat(notes.getBody()).isEqualTo("notifications:/api/v1/notifications rid=trace-123");
        assertThat(notes.getHeaders().getFirst("X-Request-Id")).isEqualTo("trace-123");
    }

    @Test
    void unknownPathsAreNotForwarded() {
        var status = RestClient.create("http://localhost:" + port).get().uri("/internal/admin")
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(status).isEqualTo(404);
    }

    private static HttpServer backend(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                String rid = exchange.getRequestHeaders().getFirst("X-Request-Id");
                byte[] body = (name + ":" + exchange.getRequestURI().getPath() + " rid=" + rid)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
