package com.stock.platform.backend_api.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateStockRequestDto(
        @NotBlank String symbol,
        String name,
        String wikiUrl,
        List<String> indexSymbols
) {
}

