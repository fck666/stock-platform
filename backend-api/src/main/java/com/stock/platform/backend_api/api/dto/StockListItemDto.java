package com.stock.platform.backend_api.api.dto;

public record StockListItemDto(
        String symbol,
        String name,
        String gicsSector,
        String gicsSubIndustry,
        String headquarters,
        String wikiDescription
) {
}

