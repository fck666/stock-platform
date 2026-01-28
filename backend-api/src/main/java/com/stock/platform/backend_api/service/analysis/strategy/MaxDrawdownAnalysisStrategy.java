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
public class MaxDrawdownAnalysisStrategy implements AnalysisStrategy {
    private final MarketRepository marketRepository;

    public MaxDrawdownAnalysisStrategy(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    @Override
    public String getType() {
        return "MAX_DRAWDOWN";
    }

    @Override
    public List<AnalysisResultDto> execute(AnalysisRequestDto request) {
        List<StockListItemDto> stocks = marketRepository.getAllIndexStocks(request.index());
        List<AnalysisResultDto> results = new ArrayList<>();

        for (StockListItemDto stock : stocks) {
            List<BarDto> bars = marketRepository.getBarsBySymbol(stock.symbol(), "1d", request.start(), request.end());
            if (bars.size() < 2) continue;

            double maxDrawdown = 0.0;
            double peak = Double.NEGATIVE_INFINITY;

            for (BarDto bar : bars) {
                double price = bar.close().doubleValue();
                if (price > peak) {
                    peak = price;
                }
                double drawdown = (price - peak) / peak;
                if (drawdown < maxDrawdown) {
                    maxDrawdown = drawdown;
                }
            }

            Map<String, Object> details = new HashMap<>();
            details.put("maxDrawdown", maxDrawdown);
            details.put("peak", peak);
            details.put("totalDays", bars.size());

            // Score is absolute drawdown for easier sorting (smaller is better, but usually we show negative)
            results.add(new AnalysisResultDto(stock.symbol(), stock.name(), maxDrawdown, details));
        }

        // Default: sort by least drawdown (highest score, since drawdown is negative)
        return results.stream()
                .sorted(Comparator.comparing(AnalysisResultDto::score).reversed())
                .limit(request.limit() != null ? request.limit() : 20)
                .collect(Collectors.toList());
    }
}
