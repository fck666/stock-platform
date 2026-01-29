package com.stock.platform.backend_api.api.dto;

import java.util.List;

public record RsSeriesDto(
        String symbol,
        String indexSymbol,
        String start,
        String end,
        Double stockReturnPct,
        Double indexReturnPct,
        Double rsReturnPct,
        List<RsPointDto> points
) {
}

