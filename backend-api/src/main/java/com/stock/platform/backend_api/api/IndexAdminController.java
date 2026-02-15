package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.CreateIndexRequestDto;
import com.stock.platform.backend_api.api.dto.IndexListItemDto;
import com.stock.platform.backend_api.api.dto.UpdateIndexConstituentsRequestDto;
import com.stock.platform.backend_api.repository.MarketRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/indices")
public class IndexAdminController {
    private final MarketRepository market;

    public IndexAdminController(MarketRepository market) {
        this.market = market;
    }

    @GetMapping
    public List<IndexListItemDto> listIndices() {
        return market.listIndices();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('admin.index.write')")
    public IndexListItemDto createIndex(@Valid @RequestBody CreateIndexRequestDto req) {
        String symbol = normalizeIndexSymbol(req.symbol());
        String name = normalizeOptional(req.name());
        String wikiUrl = normalizeOptional(req.wikiUrl());
        market.createSecurity("INDEX", symbol, name, wikiUrl, null);
        if (req.initialStockSymbols() != null && !req.initialStockSymbols().isEmpty()) {
            List<String> members = req.initialStockSymbols().stream().map(IndexAdminController::normalizeStockSymbol).toList();
            market.replaceIndexConstituents(symbol, members, LocalDate.now());
        }
        return new IndexListItemDto(symbol, name, wikiUrl);
    }

    @GetMapping("/{symbol}/constituents")
    @PreAuthorize("hasAuthority('admin.index.write')")
    public List<String> getConstituents(@PathVariable("symbol") String symbol) {
        String idx = normalizeIndexSymbol(symbol);
        return market.listIndexConstituentSymbols(idx);
    }

    @PutMapping("/{symbol}/constituents")
    @PreAuthorize("hasAuthority('admin.index.write')")
    public void replaceConstituents(@PathVariable("symbol") String symbol, @Valid @RequestBody UpdateIndexConstituentsRequestDto req) {
        String idx = normalizeIndexSymbol(symbol);
        List<String> members = req.stockSymbols().stream().map(IndexAdminController::normalizeStockSymbol).distinct().toList();
        market.replaceIndexConstituents(idx, members, LocalDate.now());
    }

    private static String normalizeOptional(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private static String normalizeIndexSymbol(String symbol) {
        if (symbol == null) throw new IllegalArgumentException("Index symbol is required");
        String s = symbol.trim().toUpperCase();
        if (!s.startsWith("^")) {
            throw new IllegalArgumentException("Index symbol must start with '^'");
        }
        if (!s.matches("^\\^[A-Z0-9_\\-]{2,20}$")) {
            throw new IllegalArgumentException("Invalid index symbol: " + s);
        }
        return s;
    }

    private static String normalizeStockSymbol(String symbol) {
        if (symbol == null) throw new IllegalArgumentException("Stock symbol is required");
        String s = symbol.trim().toUpperCase();
        if (s.startsWith("^")) {
            throw new IllegalArgumentException("Stock symbol must not start with '^'");
        }
        if (s.matches("^\\d{1,5}$")) {
            return String.format("%05d", Integer.parseInt(s));
        }
        if (!s.matches("^[A-Z0-9\\.\\-]{1,20}$")) {
            throw new IllegalArgumentException("Invalid stock symbol: " + s);
        }
        return s;
    }
}
