package com.stock.platform.backend_api.api.dto;

public record ScreenerItemDto(
        String symbol,
        String name,
        String asOfDate,
        Double close,
        Double returnPct,
        Double ma50,
        Double ma200,
        Long volume
) {
}

