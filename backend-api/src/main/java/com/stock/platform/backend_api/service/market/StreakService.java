package com.stock.platform.backend_api.service.market;

import com.stock.platform.backend_api.api.dto.StreakRankItemDto;
import com.stock.platform.backend_api.repository.MarketRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StreakService {
    private final MarketRepository market;

    public StreakService(MarketRepository market) {
        this.market = market;
    }

    public List<StreakRankItemDto> rank(StreakQuery query) {
        String index = normalizeIndexSymbol(query.universeIndexSymbol());
        BarInterval interval = query.interval() == null ? BarInterval.D1 : query.interval();
        StreakDirection direction = query.direction() == null ? StreakDirection.UP : query.direction();
        LocalDate end = query.end() != null ? query.end() : LocalDate.now().minusDays(1);
        LocalDate start = query.start() != null ? query.start() : end.minusYears(1);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start must be <= end");
        }
        int limit = clamp(query.limit() == null ? 20 : query.limit(), 5, 200);
        double volumeMultiple = sanitizeVolumeMultiple(query.volumeMultiple());
        double flatThresholdPct = sanitizeFlatThresholdPct(query.flatThresholdPct());
        return market.rankLongestStreaks(index, interval.value(), direction.sign(), start, end, limit, volumeMultiple, flatThresholdPct);
    }

    public StreakRankItemDto longestForSymbol(StreakQuery query) {
        String symbol = query.stockSymbol() == null ? "" : query.stockSymbol().trim().toUpperCase();
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        BarInterval interval = query.interval() == null ? BarInterval.D1 : query.interval();
        StreakDirection direction = query.direction() == null ? StreakDirection.UP : query.direction();
        LocalDate end = query.end() != null ? query.end() : LocalDate.now().minusDays(1);
        LocalDate start = query.start() != null ? query.start() : end.minusYears(2);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start must be <= end");
        }
        double volumeMultiple = sanitizeVolumeMultiple(query.volumeMultiple());
        double flatThresholdPct = sanitizeFlatThresholdPct(query.flatThresholdPct());
        return market.getLongestStreakForSymbol(symbol, interval.value(), direction.sign(), start, end, volumeMultiple, flatThresholdPct);
    }

    private static int clamp(int v, int min, int max) {
        return Math.min(Math.max(v, min), max);
    }

    private static double sanitizeVolumeMultiple(Double v) {
        if (v == null) return 0.0;
        if (v <= 1.0) return 0.0;
        return Math.min(v, 20.0);
    }

    private static double sanitizeFlatThresholdPct(Double v) {
        if (v == null) return 0.0;
        if (v <= 0) return 0.0;
        return Math.min(v, 5.0);
    }

    private static String normalizeIndexSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "^SPX";
        }
        return symbol.trim().toUpperCase();
    }
}
