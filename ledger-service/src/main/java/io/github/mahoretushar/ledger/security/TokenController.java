package io.github.mahoretushar.ledger.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class TokenController {

    private final JwtEncoder encoder;
    private final JwtProperties props;

    public TokenController(JwtEncoder encoder, JwtProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    @PostMapping("/token")
    public TokenResponse token(Authentication auth) {
        Instant now = Instant.now();
        Duration ttl = Duration.ofMinutes(props.ttlMinutes());
        String scope = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(" "));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ledger-service")
                .subject(auth.getName())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("scope", scope)
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds());
    }
}
