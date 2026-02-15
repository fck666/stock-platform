package com.stock.platform.backend_api.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequestDto(
        @NotBlank String oldPassword,
        @NotBlank String newPassword
) {}
