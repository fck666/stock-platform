package com.stock.platform.backend_api.api.dto;

import java.util.List;
import java.util.UUID;

public record AdminUserDto(
        UUID userId,
        String username,
        String status,
        List<String> roles
) {
}
