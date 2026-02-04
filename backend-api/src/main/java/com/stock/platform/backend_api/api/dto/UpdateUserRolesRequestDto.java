package com.stock.platform.backend_api.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateUserRolesRequestDto(
        @NotNull
        @Size(max = 50)
        List<String> roles
) {
}
