package com.stock.platform.backend_api.api.dto;

public record RsRankItemDto(
        String symbol,
        String name,
        String asOfDate,
        Double stockReturnPct,
        Double indexReturnPct,
        Double rsReturnPct
) {
}

