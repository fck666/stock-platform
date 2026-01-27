package com.stock.platform.backend_api.api.dto;

import java.time.LocalDate;
import java.util.List;

public record StockDetailDto(
        String symbol,
        String name,
        String gicsSector,
        String gicsSubIndustry,
        String headquarters,
        LocalDate dateFirstAdded,
        String cik,
        String founded,
        String wikiUrl,
        String wikiTitle,
        String wikiDescription,
        String wikiExtract,
        List<SecurityIdentifierDto> identifiers
) {
}

