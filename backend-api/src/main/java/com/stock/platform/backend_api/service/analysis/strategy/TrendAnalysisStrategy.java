package com.stock.platform.backend_api.service.analysis.strategy;

import com.stock.platform.backend_api.api.dto.AnalysisRequestDto;
import com.stock.platform.backend_api.api.dto.AnalysisResultDto;
import com.stock.platform.backend_api.api.dto.BarDto;
import com.stock.platform.backend_api.api.dto.StockListItemDto;
import com.stock.platform.backend_api.repository.MarketRepository;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class TrendAnalysisStrategy implements AnalysisStrategy {
    private final MarketRepository marketRepository;

    public TrendAnalysisStrategy(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    @Override
    public String getType() {
        return "TREND";
    }

    @Override
    public List<AnalysisResultDto> execute(AnalysisRequestDto request) {
        List<StockListItemDto> stocks = marketRepository.getAllIndexStocks(request.index());
        List<AnalysisResultDto> results = new ArrayList<>();

        for (StockListItemDto stock : stocks) {
            List<BarDto> bars = marketRepository.getBarsBySymbol(stock.symbol(), "1d", request.start(), request.end());
            if (bars.size() < 10) continue; // Not enough data

            SimpleRegression regression = new SimpleRegression();
            double startPrice = bars.get(0).close().doubleValue();
            
            for (int i = 0; i < bars.size(); i++) {
                regression.addData(i, bars.get(i).close().doubleValue());
            }

            double slope = regression.getSlope();
            double rSquared = regression.getRSquare();
            
            // Normalized slope: percentage change per day relative to start price
            double normalizedSlope = slope / startPrice;
            double score = normalizedSlope * rSquared;

            Map<String, Object> details = new HashMap<>();
            details.put("slope", slope);
            details.put("normalizedSlope", normalizedSlope);
            details.put("rSquared", rSquared);
            details.put("totalDays", bars.size());

            results.add(new AnalysisResultDto(stock.symbol(), stock.name(), score, details));
        }

        // Handle Trend Strong/Weak
        boolean strong = true;
        if (request.params() != null && "weak".equals(request.params().get("trendType"))) {
            strong = false;
        }

        Comparator<AnalysisResultDto> comparator = Comparator.comparing(AnalysisResultDto::score);
        if (strong) {
            comparator = comparator.reversed();
        }

        return results.stream()
                .sorted(comparator)
                .limit(request.limit() != null ? request.limit() : 20)
                .collect(Collectors.toList());
    }
}
