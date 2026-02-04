package com.stock.platform.backend_api.api.dto;

import java.util.List;

public record RoleDto(
        String code,
        String name,
        List<String> permissions
) {
}
