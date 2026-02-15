package com.stock.platform.backend_api.api.dto;

import jakarta.validation.constraints.NotBlank;

public record PageViewRequestDto(
        @NotBlank String path,
        String title
) {
}
