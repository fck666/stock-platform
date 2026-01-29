package com.stock.platform.backend_api.api.dto;

public record IndexListItemDto(
        String symbol,
        String name,
        String wikiUrl
) {
}

