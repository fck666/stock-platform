package com.stock.platform.backend_api.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
        @NotBlank
        String username,
        @NotBlank
        String password,
        String clientType
) {
}
