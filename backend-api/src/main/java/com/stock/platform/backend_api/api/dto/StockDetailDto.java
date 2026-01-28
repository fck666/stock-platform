package com.stock.platform.backend_api.api.dto;

import java.math.BigDecimal;
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
        Long sharesOutstanding,
        Long floatShares,
        BigDecimal marketCap,
        String currency,
        List<SecurityIdentifierDto> identifiers,
        List<CorporateActionDto> corporateActions
) {
}
