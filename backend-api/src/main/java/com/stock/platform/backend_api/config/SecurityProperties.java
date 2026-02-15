package com.stock.platform.backend_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(
        Jwt jwt,
        InitAdmin initAdmin,
        DevAuth devAuth
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

    public record DevAuth(
            boolean enabled,
            String username,
            String roles,
            boolean autoCreateUser,
            boolean onlyIfMissingAuthorization
    ) {
    }
}
