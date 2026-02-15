package com.stock.platform.backend_api.api.dto;

import java.util.List;

public record AnalyticsSummaryDto(
        List<AnalyticsSeriesPointDto> pageViews,
        List<AnalyticsSeriesPointDto> apiCalls,
        List<AnalyticsTopItemDto> topPages,
        List<AnalyticsTopApiDto> topApis,
        List<AnalyticsTopItemDto> topUsers
) {
}
