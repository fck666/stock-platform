package com.stock.platform.backend_api.api.dto;

public record TokenResponseDto(
        String accessToken,
        String refreshToken
) {
}
