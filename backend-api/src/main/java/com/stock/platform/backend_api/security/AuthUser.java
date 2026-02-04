package com.stock.platform.backend_api.security;

import java.util.UUID;

public record AuthUser(
        UUID userId,
        String username
) {
}
