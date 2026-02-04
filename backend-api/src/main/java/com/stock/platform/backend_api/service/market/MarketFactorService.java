package com.stock.platform.backend_api.service.market;

import com.stock.platform.backend_api.api.dto.FactorRankItemDto;
import com.stock.platform.backend_api.repository.MarketRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MarketFactorService {
    private final MarketRepository market;

    public MarketFactorService(MarketRepository market) {
        this.market = market;
    }

    public List<FactorRankItemDto> rank(FactorRankQuery query) {
        String index = normalizeIndexSymbol(query.universeIndexSymbol());
        BarInterval interval = query.interval() == null ? BarInterval.D1 : query.interval();
        FactorMetric metric = query.metric() == null ? FactorMetric.MAX_DRAWDOWN : query.metric();
        LocalDate end = query.end() != null ? query.end() : LocalDate.now().minusDays(1);
        LocalDate start = query.start() != null ? query.start() : end.minusYears(1);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start must be <= end");
        }
        int limit = clamp(query.limit() == null ? 20 : query.limit(), 5, 200);

        return switch (metric) {
            case MAX_DRAWDOWN -> {
                boolean best = "best".equalsIgnoreCase(query.mode()) || "resilient".equalsIgnoreCase(query.mode());
                yield market.rankMaxDrawdown(index, interval.value(), start, end, limit, best);
            }
            case MAX_RUNUP -> market.rankMaxRunup(index, interval.value(), start, end, limit);
            case MAX_RUNDOWN -> market.rankMaxRundown(index, interval.value(), start, end, limit);
            case NEW_HIGH_COUNT -> {
                int lookback = clamp(query.lookback() == null ? defaultLookback(interval) : query.lookback(), 2, 2000);
                yield market.rankNewHighLowCounts(index, interval.value(), start, end, limit, lookback, true);
            }
            case NEW_LOW_COUNT -> {
                int lookback = clamp(query.lookback() == null ? defaultLookback(interval) : query.lookback(), 2, 2000);
                yield market.rankNewHighLowCounts(index, interval.value(), start, end, limit, lookback, false);
            }
        };
    }

    private static int defaultLookback(BarInterval interval) {
        if (interval == BarInterval.W1) return 52;
        if (interval == BarInterval.M1) return 24;
        return 252;
    }

    private static int clamp(int v, int min, int max) {
        return Math.min(Math.max(v, min), max);
    }

    private static String normalizeIndexSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "^SPX";
        }
        return symbol.trim().toUpperCase();
    }
}

