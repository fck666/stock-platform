package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.BarDto;
import com.stock.platform.backend_api.repository.MarketRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/index")
public class IndexController {
    private final MarketRepository marketRepository;

    public IndexController(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
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
}

