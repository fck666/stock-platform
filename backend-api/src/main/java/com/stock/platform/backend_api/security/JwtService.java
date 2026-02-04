package com.stock.platform.backend_api.security;

import com.stock.platform.backend_api.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtService {
    private final SecretKey key;
    private final long accessTokenTtlSeconds;

    public JwtService(SecurityProperties props) {
        SecurityProperties.Jwt jwt = props.jwt();
        this.accessTokenTtlSeconds = jwt.accessTokenTtlSeconds();
        this.key = buildKey(jwt.secret());
    }

    public String createAccessToken(AuthUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTokenTtlSeconds);
        return Jwts.builder()
                .subject(user.userId().toString())
                .claim("u", user.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public Optional<JwtSubject> parseAccessToken(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            Claims claims = jws.getPayload();
            String subject = claims.getSubject();
            String username = claims.get("u", String.class);
            if (subject == null || subject.isBlank()) return Optional.empty();
            return Optional.of(new JwtSubject(subject, username));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static SecretKey buildKey(String configured) {
        if (configured != null && !configured.isBlank()) {
            byte[] raw = configured.getBytes(StandardCharsets.UTF_8);
            if (raw.length >= 32) {
                return Keys.hmacShaKeyFor(raw);
            }
        }
        byte[] raw = new byte[64];
        new SecureRandom().nextBytes(raw);
        return Keys.hmacShaKeyFor(raw);
    }
}
