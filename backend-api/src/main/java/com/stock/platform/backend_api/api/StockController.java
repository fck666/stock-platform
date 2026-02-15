package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.BarDto;
import com.stock.platform.backend_api.api.dto.CorporateActionDto;
import com.stock.platform.backend_api.api.dto.IndicatorsResponseDto;
import com.stock.platform.backend_api.api.dto.PagedResponse;
import com.stock.platform.backend_api.api.dto.StockDetailDto;
import com.stock.platform.backend_api.api.dto.StockListItemDto;
import com.stock.platform.backend_api.repository.MarketRepository;
import com.stock.platform.backend_api.service.IndicatorsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
/**
 * REST Controller for Stock Data.
 * Provides endpoints for listing stocks, getting details, historical bars, and technical indicators.
 */
public class StockController {
    private final MarketRepository marketRepository;
    private final IndicatorsService indicatorsService;

    public StockController(MarketRepository marketRepository, IndicatorsService indicatorsService) {
        this.marketRepository = marketRepository;
        this.indicatorsService = indicatorsService;
    }

    /**
     * List stocks with pagination and filtering.
     *
     * @param index Filter by index symbol (default: ^SPX)
     * @param query Search query for symbol or name
     * @param page Page number (0-based)
     * @param size Page size (default: 50, max: 200)
     * @param sortBy Field to sort by
     * @param sortDir Sort direction (asc/desc)
     * @return Paged response of stock items
     */
    @GetMapping
    public PagedResponse<StockListItemDto> listStocks(
            @RequestParam(defaultValue = "^SPX") String index,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(defaultValue = "en") String lang
    ) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        String idx = index == null ? "^SPX" : index.trim().toUpperCase();
        return marketRepository.listIndexStocks(idx, query, safePage, safeSize, sortBy, sortDir, lang);
    }

    /**
     * Get detailed information for a specific stock.
     *
     * @param symbol Stock symbol
     * @return Stock detail DTO
     */
    @GetMapping("/{symbol}")
    public StockDetailDto getStockDetail(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "en") String lang
    ) {
        return marketRepository.getStockDetail(symbol.toUpperCase(), lang);
    }

    /**
     * Get historical price bars (OHLCV) for a stock.
     * 
     * Default Behavior:
     * - Returns last 2 years of daily data if no dates specified.
     * - Ends at yesterday if no end date specified.
     *
     * @param symbol Stock symbol (case insensitive)
     * @param interval Time interval (1d, 1w, 1m, etc.)
     * @param start Start date (YYYY-MM-DD)
     * @param end End date (YYYY-MM-DD)
     * @return List of bars sorted by date
     */
    @GetMapping("/{symbol}/bars")
    public List<BarDto> getStockBars(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        LocalDate effectiveEnd = end != null ? end : LocalDate.now().minusDays(1);
        LocalDate effectiveStart = start != null ? start : effectiveEnd.minusYears(2);
        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("start must be <= end");
        }
        return marketRepository.getBarsBySymbol(symbol.toUpperCase(), interval, effectiveStart, effectiveEnd);
    }

    /**
     * Get calculated indicators (MA, MACD, KDJ) for a stock.
     * 
     * Parameters:
     * - `ma`: Comma-separated integers for Moving Average periods (e.g., "20,50,200").
     * - `include`: Comma-separated strings for other indicators (supported: "macd", "kdj").
     *
     * @param symbol Stock symbol
     * @param interval Time interval
     * @param ma Comma-separated list of MA periods
     * @param include Comma-separated list of other indicators
     * @return Indicator response containing time-series data points
     */
    @GetMapping("/{symbol}/indicators")
    public IndicatorsResponseDto getIndicators(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String ma,
            @RequestParam(required = false) String include
    ) {
        List<Integer> maPeriods = parseIntCsv(ma);
        String inc = include == null ? "" : include.toLowerCase();
        boolean macd = inc.contains("macd");
        boolean kdj = inc.contains("kdj");
        return indicatorsService.getIndicators(symbol.toUpperCase(), interval, start, end, maPeriods, macd, kdj);
    }

    @GetMapping("/{symbol}/corporate-actions")
    public List<CorporateActionDto> getCorporateActions(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        LocalDate effectiveEnd = end != null ? end : LocalDate.now().minusDays(1);
        LocalDate effectiveStart = start != null ? start : effectiveEnd.minusYears(2);
        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("start must be <= end");
        }
        return marketRepository.listCorporateActionsBySymbol(symbol.toUpperCase(), effectiveStart, effectiveEnd);
    }

    private static List<Integer> parseIntCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(v -> v != null && v > 0)
                .toList();
    }
}
