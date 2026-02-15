package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.BreadthSnapshotDto;
import com.stock.platform.backend_api.api.dto.RsRankItemDto;
import com.stock.platform.backend_api.api.dto.RsSeriesDto;
import com.stock.platform.backend_api.api.dto.ScreenerItemDto;
import com.stock.platform.backend_api.repository.MarketRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketInsightsController {
    private final MarketRepository market;

    public MarketInsightsController(MarketRepository market) {
        this.market = market;
    }

    @GetMapping("/breadth")
    public BreadthSnapshotDto getBreadth(
            @RequestParam(defaultValue = "^SPX") String index,
            @RequestParam(defaultValue = "2.0") double volumeSurgeMultiple
    ) {
        String idx = index == null ? "^SPX" : index.trim().toUpperCase();
        double multiple = Math.max(1.0, Math.min(volumeSurgeMultiple, 20.0));
        return market.getBreadthSnapshot(idx, multiple);
    }

    @GetMapping("/breadth/detail")
    public List<ScreenerItemDto> getBreadthDetail(
            @RequestParam(defaultValue = "^SPX") String index,
            @RequestParam String metric,
            @RequestParam(defaultValue = "2.0") double volumeSurgeMultiple
    ) {
        String idx = index == null ? "^SPX" : index.trim().toUpperCase();
        String m = metric == null ? "" : metric.trim().toLowerCase();
        double multiple = Math.max(1.0, Math.min(volumeSurgeMultiple, 20.0));
        return market.getBreadthDetail(idx, m, multiple);
    }

    @GetMapping("/screener")
    public List<ScreenerItemDto> screener(
            @RequestParam(defaultValue = "^SPX") String index,
            @RequestParam(defaultValue = "trend") String preset,
            @RequestParam(defaultValue = "126") int lookbackDays,
            @RequestParam(defaultValue = "20") int limit
    ) {
        String idx = index == null ? "^SPX" : index.trim().toUpperCase();
        String p = preset == null ? "trend" : preset.trim().toLowerCase();
        int lb = Math.min(Math.max(lookbackDays, 5), 252);
        int lim = Math.min(Math.max(limit, 5), 200);
        return market.runScreener(idx, p, lb, lim);
    }

    @GetMapping("/rs")
    public RsSeriesDto getRelativeStrength(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "^SPX") String index,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end
    ) {
        String sym = symbol == null ? "" : symbol.trim().toUpperCase();
        String idx = index == null ? "^SPX" : index.trim().toUpperCase();
        LocalDate effectiveEnd = end != null ? end : LocalDate.now().minusDays(1);
        LocalDate effectiveStart = start != null ? start : effectiveEnd.minusYears(2);
        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("start must be <= end");
        }
        return market.getRelativeStrengthSeries(sym, idx, effectiveStart, effectiveEnd);
    }

    @GetMapping("/rs/rank")
    public List<RsRankItemDto> rankRelativeStrength(
            @RequestParam(defaultValue = "^SPX") String index,
            @RequestParam(defaultValue = "126") int lookbackDays,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "true") boolean requireAboveMa50
    ) {
        String idx = index == null ? "^SPX" : index.trim().toUpperCase();
        int lb = Math.min(Math.max(lookbackDays, 5), 252);
        int lim = Math.min(Math.max(limit, 5), 200);
        return market.rankRelativeStrength(idx, lb, lim, requireAboveMa50);
    }
}
