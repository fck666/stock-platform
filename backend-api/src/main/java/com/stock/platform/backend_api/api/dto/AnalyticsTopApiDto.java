package com.stock.platform.backend_api.api.dto;

public record AnalyticsTopApiDto(
        String method,
        String path,
        long count,
        long errorCount,
        long p95LatencyMs
) {
}
