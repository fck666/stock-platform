package com.stock.platform.backend_api.security;

import com.stock.platform.backend_api.config.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    @Test
    void roundTripAccessToken() {
        SecurityProperties props = new SecurityProperties(
                new SecurityProperties.Jwt("0123456789abcdef0123456789abcdef", 60, 3600),
                null,
                null
        );
        JwtService jwt = new JwtService(props);
        AuthUser user = new AuthUser(UUID.randomUUID(), "fcc");
        String token = jwt.createAccessToken(user);

        var parsed = jwt.parseAccessToken(token).orElseThrow();
        assertEquals(user.userId().toString(), parsed.userId());
        assertEquals(user.username(), parsed.username());
    }
}
