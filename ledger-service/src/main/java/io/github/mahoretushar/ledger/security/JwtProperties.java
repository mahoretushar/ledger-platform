package io.github.mahoretushar.ledger.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ledger.jwt")
public record JwtProperties(String secret, long ttlMinutes) {
}
