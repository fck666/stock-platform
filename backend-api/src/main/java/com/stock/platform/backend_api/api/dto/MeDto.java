package com.stock.platform.backend_api.api.dto;

import java.util.List;
import java.util.UUID;

public record MeDto(
        UUID userId,
        String username,
        List<String> roles,
        List<String> permissions
) {
}
