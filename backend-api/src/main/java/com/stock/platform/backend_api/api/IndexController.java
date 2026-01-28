package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.BarDto;
import com.stock.platform.backend_api.api.dto.IndicatorsResponseDto;
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
@RequestMapping("/api/index")
public class IndexController {
    private final MarketRepository marketRepository;
    private final IndicatorsService indicatorsService;

    public IndexController(MarketRepository marketRepository, IndicatorsService indicatorsService) {
        this.marketRepository = marketRepository;
        this.indicatorsService = indicatorsService;
    }

    @GetMapping("/{symbol}/bars")
    public List<BarDto> getIndexBars(
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
        
        String canonicalSymbol = symbol.toUpperCase();
        if ("SP500".equals(canonicalSymbol)) canonicalSymbol = "^SPX";
        if (!canonicalSymbol.startsWith("^")) canonicalSymbol = "^" + canonicalSymbol;

        return marketRepository.getBarsBySymbol(canonicalSymbol, interval, effectiveStart, effectiveEnd);
    }

    @GetMapping("/{symbol}/indicators")
    public IndicatorsResponseDto getIndexIndicators(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) String ma,
            @RequestParam(required = false) String include
    ) {
        LocalDate effectiveEnd = end != null ? end : LocalDate.now().minusDays(1);
        LocalDate effectiveStart = start != null ? start : effectiveEnd.minusYears(2);
        if (effectiveStart.isAfter(effectiveEnd)) {
            throw new IllegalArgumentException("start must be <= end");
        }

        String canonicalSymbol = symbol.toUpperCase();
        if ("SP500".equals(canonicalSymbol)) canonicalSymbol = "^SPX";
        if (!canonicalSymbol.startsWith("^")) canonicalSymbol = "^" + canonicalSymbol;

        List<Integer> maPeriods = parseIntCsv(ma);
        String inc = include == null ? "" : include.toLowerCase();
        boolean macd = inc.contains("macd");
        boolean kdj = inc.contains("kdj");
        return indicatorsService.getIndicators(canonicalSymbol, interval, effectiveStart, effectiveEnd, maPeriods, macd, kdj);
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
