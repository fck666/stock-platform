package com.stock.platform.backend_api.api.dto;

import java.time.LocalDate;
import java.util.Map;

public record AnalysisRequestDto(
        String index,           // e.g., "^SPX"
        String type,            // e.g., "TREND", "WIN_RATE"
        LocalDate start,
        LocalDate end,
        Integer limit,
        Map<String, Object> params // Extra params like "trendType": "strong"/"weak", "threshold": 0.9
) {}
