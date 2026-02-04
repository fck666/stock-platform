package com.stock.platform.backend_api.security;

public record JwtSubject(
        String userId,
        String username
) {
}
