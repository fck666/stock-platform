package com.stock.platform.backend_api.api.dto;

import java.util.List;

public record AnalysisResponseDto(
        String type,
        List<AnalysisResultDto> results
) {}
