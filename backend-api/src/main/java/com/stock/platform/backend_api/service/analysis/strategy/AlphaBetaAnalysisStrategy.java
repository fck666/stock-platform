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
public class AlphaBetaAnalysisStrategy implements AnalysisStrategy {
    private final MarketRepository marketRepository;

    public AlphaBetaAnalysisStrategy(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    @Override
    public String getType() {
        return "ALPHA_BETA";
    }

    @Override
    public List<AnalysisResultDto> execute(AnalysisRequestDto request) {
        // 1. Get Index Bars as benchmark
        String benchmarkSymbol = request.index().equals("ALL") ? "^SPX" : request.index();
        List<BarDto> indexBars = marketRepository.getBarsBySymbol(benchmarkSymbol, "1d", request.start(), request.end());
        if (indexBars.size() < 10) return Collections.emptyList();

        Map<java.time.LocalDate, Double> indexReturns = calculateDailyReturns(indexBars);

        List<StockListItemDto> stocks = marketRepository.getAllIndexStocks(request.index());
        List<AnalysisResultDto> results = new ArrayList<>();

        for (StockListItemDto stock : stocks) {
            List<BarDto> stockBars = marketRepository.getBarsBySymbol(stock.symbol(), "1d", request.start(), request.end());
            if (stockBars.size() < 10) continue;

            Map<java.time.LocalDate, Double> stockReturns = calculateDailyReturns(stockBars);
            
            SimpleRegression regression = new SimpleRegression();
            int points = 0;
            for (Map.Entry<java.time.LocalDate, Double> entry : indexReturns.entrySet()) {
                Double sReturn = stockReturns.get(entry.getKey());
                if (sReturn != null) {
                    regression.addData(entry.getValue(), sReturn);
                    points++;
                }
            }

            if (points < 10) continue;

            double alpha = regression.getIntercept();
            double beta = regression.getSlope();
            double rSquare = regression.getRSquare();

            Map<String, Object> details = new HashMap<>();
            details.put("alpha", alpha);
            details.put("beta", beta);
            details.put("rSquare", rSquare);
            details.put("benchmark", benchmarkSymbol);

            // Default score is Alpha (Excess return)
            results.add(new AnalysisResultDto(stock.symbol(), stock.name(), alpha, details));
        }

        boolean sortByBeta = request.params() != null && "beta".equals(request.params().get("sortType"));
        
        Comparator<AnalysisResultDto> comparator;
        if (sortByBeta) {
            // If sorting by Beta, usually we want higher beta or absolute beta? 
            // Let's go with highest beta.
            comparator = Comparator.comparing(r -> (Double) r.details().get("beta"));
        } else {
            comparator = Comparator.comparing(AnalysisResultDto::score);
        }

        return results.stream()
                .sorted(comparator.reversed())
                .limit(request.limit() != null ? request.limit() : 20)
                .collect(Collectors.toList());
    }

    private Map<java.time.LocalDate, Double> calculateDailyReturns(List<BarDto> bars) {
        Map<java.time.LocalDate, Double> returns = new HashMap<>();
        for (int i = 1; i < bars.size(); i++) {
            double prev = bars.get(i - 1).close().doubleValue();
            double curr = bars.get(i).close().doubleValue();
            if (prev > 0) {
                returns.put(bars.get(i).date(), (curr - prev) / prev);
            }
        }
        return returns;
    }
}
