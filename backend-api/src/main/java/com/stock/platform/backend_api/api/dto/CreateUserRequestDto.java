package com.stock.platform.backend_api.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateUserRequestDto(
        @NotBlank String username,
        @NotBlank String password,
        List<String> roles
) {}
