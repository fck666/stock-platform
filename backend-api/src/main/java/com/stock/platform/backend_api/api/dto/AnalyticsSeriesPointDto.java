package com.stock.platform.backend_api.api.dto;

import java.time.LocalDate;

public record AnalyticsSeriesPointDto(
        LocalDate day,
        long count
) {
}
