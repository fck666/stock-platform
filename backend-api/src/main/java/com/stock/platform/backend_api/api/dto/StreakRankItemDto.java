package com.stock.platform.backend_api.api.dto;

public record StreakRankItemDto(
        String symbol,
        String name,
        String interval,
        String direction,
        Integer streak,
        String startDate,
        String endDate
) {
}

