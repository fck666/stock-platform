package com.stock.platform.backend_api.service.analysis.strategy;

import com.stock.platform.backend_api.api.dto.AnalysisRequestDto;
import com.stock.platform.backend_api.api.dto.AnalysisResultDto;
import com.stock.platform.backend_api.api.dto.BarDto;
import com.stock.platform.backend_api.api.dto.StockListItemDto;
import com.stock.platform.backend_api.repository.MarketRepository;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class VolumeSpikeAnalysisStrategy implements AnalysisStrategy {
    private final MarketRepository marketRepository;

    public VolumeSpikeAnalysisStrategy(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    @Override
    public String getType() {
        return "VOLUME_SPIKE";
    }

    @Override
    public List<AnalysisResultDto> execute(AnalysisRequestDto request) {
        List<StockListItemDto> stocks = marketRepository.getAllIndexStocks(request.index());
        List<AnalysisResultDto> results = new ArrayList<>();

        for (StockListItemDto stock : stocks) {
            // Get data including a lookback for baseline volume
            List<BarDto> bars = marketRepository.getBarsBySymbol(stock.symbol(), "1d", request.start().minusDays(30), request.end());
            if (bars.isEmpty()) continue;

            // Split into baseline (before start) and target (after start)
            List<BarDto> baselineBars = new ArrayList<>();
            List<BarDto> targetBars = new ArrayList<>();
            for (BarDto bar : bars) {
                if (bar.date().isBefore(request.start())) {
                    baselineBars.add(bar);
                } else {
                    targetBars.add(bar);
                }
            }

            if (targetBars.isEmpty()) continue;

            double avgBaselineVolume;
            if (baselineBars.isEmpty()) {
                // If no baseline, use target's own average (less ideal)
                avgBaselineVolume = targetBars.stream().mapToLong(b -> b.volume() != null ? b.volume() : 0).average().orElse(0);
            } else {
                avgBaselineVolume = baselineBars.stream().mapToLong(b -> b.volume() != null ? b.volume() : 0).average().orElse(0);
            }

            if (avgBaselineVolume <= 0) continue;

            double maxVolume = targetBars.stream().mapToLong(b -> b.volume() != null ? b.volume() : 0).max().orElse(0);
            double spikeMultiplier = maxVolume / avgBaselineVolume;

            Map<String, Object> details = new HashMap<>();
            details.put("spikeMultiplier", spikeMultiplier);
            details.put("avgBaselineVolume", avgBaselineVolume);
            details.put("maxVolume", maxVolume);

            results.add(new AnalysisResultDto(stock.symbol(), stock.name(), spikeMultiplier, details));
        }

        return results.stream()
                .sorted(Comparator.comparing(AnalysisResultDto::score).reversed())
                .limit(request.limit() != null ? request.limit() : 20)
                .collect(Collectors.toList());
    }
}
