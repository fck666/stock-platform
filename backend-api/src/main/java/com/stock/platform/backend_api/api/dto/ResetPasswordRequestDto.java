package com.stock.platform.backend_api.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequestDto(
        @NotBlank String adminPassword,
        @NotBlank String newPassword
) {}
