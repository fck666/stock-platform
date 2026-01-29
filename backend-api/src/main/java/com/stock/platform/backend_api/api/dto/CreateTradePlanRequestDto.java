package com.stock.platform.backend_api.api.dto;

import java.time.LocalDate;

public record CreateTradePlanRequestDto(
        String symbol,
        String direction,
        String status,
        LocalDate startDate,
        Double entryPrice,
        Double entryLow,
        Double entryHigh,
        Double stopPrice,
        Double targetPrice,
        String note
) {
}

