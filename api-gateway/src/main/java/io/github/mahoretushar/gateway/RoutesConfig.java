package io.github.mahoretushar.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.UUID;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

/**
 * One public entry point. Authentication stays in the services (they validate the JWT themselves), so a
 * request that bypasses the gateway is still protected; the gateway adds routing and request tracing.
 */
@Configuration
class RoutesConfig {

    static final String REQUEST_ID = "X-Request-Id";

    @Bean
    RouterFunction<ServerResponse> ledgerRoutes(@Value("${gateway.ledger-uri}") String ledgerUri) {
        return route("ledger-service")
                .route(path("/api/v1/auth/**", "/api/v1/accounts", "/api/v1/accounts/**", "/api/v1/transfers/**",
                        "/api/v1/transfers"), http())
                .before(uri(ledgerUri))
                .before(RoutesConfig::ensureRequestId)
                .after(RoutesConfig::echoRequestId)
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> notificationRoutes(@Value("${gateway.notification-uri}") String notificationUri) {
        return route("notification-service")
                .route(path("/api/v1/notifications", "/api/v1/notifications/**"), http())
                .before(uri(notificationUri))
                .before(RoutesConfig::ensureRequestId)
                .after(RoutesConfig::echoRequestId)
                .build();
    }

    /** Keeps a caller-supplied request ID or creates one, so one ID follows the request through every service log. */
    static ServerRequest ensureRequestId(ServerRequest request) {
        String id = request.headers().firstHeader(REQUEST_ID);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        String finalId = id;
        request.attributes().put(REQUEST_ID, finalId);
        return ServerRequest.from(request).headers(h -> h.set(REQUEST_ID, finalId)).build();
    }

    static ServerResponse echoRequestId(ServerRequest request, ServerResponse response) {
        Object id = request.attributes().get(REQUEST_ID);
        if (id != null) {
            response.headers().set(REQUEST_ID, id.toString());
        }
        return response;
    }
}
