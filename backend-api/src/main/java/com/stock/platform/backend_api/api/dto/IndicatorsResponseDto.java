package com.stock.platform.backend_api.api.dto;

import java.util.List;

public record IndicatorsResponseDto(
        String interval,
        List<IndicatorPointDto> points
) {
}

