package com.stock.platform.backend_api.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateIndexRequestDto(
        @NotBlank String symbol,
        String name,
        String wikiUrl,
        List<String> initialStockSymbols
) {
}

