package com.stock.platform.backend_api.api.dto;

public record SecurityIdentifierDto(
        String provider,
        String identifier
) {
}

