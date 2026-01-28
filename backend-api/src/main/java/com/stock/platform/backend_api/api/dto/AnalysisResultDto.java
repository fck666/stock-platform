package com.stock.platform.backend_api.api.dto;

import java.util.Map;

public record AnalysisResultDto(
        String symbol,
        String name,
        Double score,
        Map<String, Object> details // e.g., slope, rSquared, winRate, totalDays
) {}
