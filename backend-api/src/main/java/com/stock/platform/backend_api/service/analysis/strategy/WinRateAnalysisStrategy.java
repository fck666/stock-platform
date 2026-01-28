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
public class WinRateAnalysisStrategy implements AnalysisStrategy {
    private final MarketRepository marketRepository;

    public WinRateAnalysisStrategy(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    @Override
    public String getType() {
        return "WIN_RATE";
    }

    @Override
    public List<AnalysisResultDto> execute(AnalysisRequestDto request) {
        List<StockListItemDto> stocks = marketRepository.getAllIndexStocks(request.index());
        List<AnalysisResultDto> results = new ArrayList<>();

        double threshold = 0.0;
        if (request.params() != null && request.params().get("threshold") != null) {
            threshold = Double.parseDouble(request.params().get("threshold").toString());
        }

        for (StockListItemDto stock : stocks) {
            List<BarDto> bars = marketRepository.getBarsBySymbol(stock.symbol(), "1d", request.start(), request.end());
            if (bars.size() < 10) continue;

            int upDays = 0;
            for (BarDto bar : bars) {
                if (bar.close().compareTo(bar.open()) > 0) {
                    upDays++;
                }
            }

            double winRate = (double) upDays / bars.size();
            
            if (winRate >= threshold) {
                Map<String, Object> details = new HashMap<>();
                details.put("winRate", winRate);
                details.put("upDays", upDays);
                details.put("totalDays", bars.size());

                results.add(new AnalysisResultDto(stock.symbol(), stock.name(), winRate, details));
            }
        }

        return results.stream()
                .sorted(Comparator.comparing(AnalysisResultDto::score).reversed())
                .limit(request.limit() != null ? request.limit() : 20)
                .collect(Collectors.toList());
    }
}
