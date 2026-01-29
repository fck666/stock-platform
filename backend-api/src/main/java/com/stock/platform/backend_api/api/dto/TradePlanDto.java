package com.stock.platform.backend_api.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TradePlanDto(
        long id,
        String symbol,
        String name,
        String direction,
        String status,
        LocalDate startDate,
        Double entryPrice,
        Double entryLow,
        Double entryHigh,
        Double stopPrice,
        Double targetPrice,
        String note,
        LocalDate lastBarDate,
        Double lastClose,
        Double pnlPct,
        Boolean hitStop,
        Boolean hitTarget,
        OffsetDateTime updatedAt
) {
}

