package com.stock.platform.backend_api.service.analysis;

import com.stock.platform.backend_api.api.dto.AnalysisRequestDto;
import com.stock.platform.backend_api.api.dto.AnalysisResponseDto;
import com.stock.platform.backend_api.api.dto.AnalysisResultDto;
import com.stock.platform.backend_api.service.analysis.strategy.AnalysisStrategy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
/**
 * Service for executing pluggable analysis strategies.
 * Strategies are discovered automatically from the Spring context.
 */
public class AnalysisService {
    private final Map<String, AnalysisStrategy> strategies = new ConcurrentHashMap<>();

    public AnalysisService(List<AnalysisStrategy> strategyList) {
        for (AnalysisStrategy strategy : strategyList) {
            strategies.put(strategy.getType(), strategy);
        }
    }

    /**
     * Execute an analysis request using the appropriate strategy.
     *
     * @param request The analysis request containing the strategy type (e.g., "trend", "volatility")
     * @return The analysis results
     */
    public AnalysisResponseDto analyze(AnalysisRequestDto request) {
        AnalysisStrategy strategy = strategies.get(request.type());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported analysis type: " + request.type());
        }

        List<AnalysisResultDto> results = strategy.execute(request);
        return new AnalysisResponseDto(request.type(), results);
    }
}
