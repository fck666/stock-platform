package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.BarDto;
import com.stock.platform.backend_api.api.dto.PagedResponse;
import com.stock.platform.backend_api.api.dto.StockDetailDto;
import com.stock.platform.backend_api.api.dto.StockListItemDto;
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
@RequestMapping("/api/stocks")
public class StockController {
    private final MarketRepository marketRepository;

    public StockController(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    @GetMapping
    public PagedResponse<StockListItemDto> listStocks(
            @RequestParam(defaultValue = "^SPX") String index,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "en") String lang
    ) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        return marketRepository.listIndexStocks(index.toUpperCase(), query, safePage, safeSize, lang);
    }

    @GetMapping("/{symbol}")
    public StockDetailDto getStockDetail(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "en") String lang
    ) {
        return marketRepository.getStockDetail(symbol.toUpperCase(), lang);
    }

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
}

