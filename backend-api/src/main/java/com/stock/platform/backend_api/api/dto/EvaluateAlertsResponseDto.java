package com.stock.platform.backend_api.api.dto;

import java.util.List;

public record EvaluateAlertsResponseDto(
        int triggered,
        List<AlertEventDto> latestEvents
) {
}

