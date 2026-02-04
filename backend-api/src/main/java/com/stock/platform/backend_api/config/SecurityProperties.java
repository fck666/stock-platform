package com.stock.platform.backend_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        Jwt jwt,
        InitAdmin initAdmin
) {
    public record Jwt(
            String secret,
            long accessTokenTtlSeconds,
            long refreshTokenTtlSeconds
    ) {
    }

    public record InitAdmin(
            String username,
            String password,
            boolean forceResetPassword
    ) {
    }
}
