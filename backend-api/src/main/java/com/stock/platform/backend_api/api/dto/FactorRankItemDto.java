package com.stock.platform.backend_api.api.dto;

public record FactorRankItemDto(
        String symbol,
        String name,
        String metric,
        Double value,
        Integer count,
        Double rate,
        String startDate,
        String endDate
) {
}

