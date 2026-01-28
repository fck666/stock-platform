package com.stock.platform.backend_api.service.analysis.strategy;

import com.stock.platform.backend_api.api.dto.AnalysisRequestDto;
import com.stock.platform.backend_api.api.dto.AnalysisResultDto;

import java.util.List;

public interface AnalysisStrategy {
    String getType();
    List<AnalysisResultDto> execute(AnalysisRequestDto request);
}
