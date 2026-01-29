package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.CreateStockRequestDto;
import com.stock.platform.backend_api.repository.MarketRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/stocks")
public class StockAdminController {
    private final MarketRepository market;

    public StockAdminController(MarketRepository market) {
        this.market = market;
    }

    @PostMapping
    public void createStock(@Valid @RequestBody CreateStockRequestDto req) {
        String symbol = normalizeStockSymbol(req.symbol());
        String name = normalizeOptional(req.name());
        String wikiUrl = normalizeOptional(req.wikiUrl());
        String stooqSymbol = inferStooqSymbol(symbol);
        market.createSecurity("STOCK", symbol, name, wikiUrl, stooqSymbol);

        List<String> indexSymbols = req.indexSymbols() == null ? List.of() : req.indexSymbols();
        if (!indexSymbols.isEmpty()) {
            Set<String> indices = new LinkedHashSet<>();
            for (String idx : indexSymbols) {
                indices.add(normalizeIndexSymbol(idx));
            }
            for (String idx : indices) {
                List<String> current;
                try {
                    current = market.listIndexConstituentSymbols(idx);
                } catch (Exception e) {
                    current = new ArrayList<>();
                }
                if (!current.contains(symbol)) {
                    current.add(symbol);
                }
                market.replaceIndexConstituents(idx, current, LocalDate.now());
            }
        }
    }

    private static String inferStooqSymbol(String canonicalSymbol) {
        if (canonicalSymbol.matches("^\\d{5}$")) {
            int n = Integer.parseInt(canonicalSymbol);
            return (n + ".hk").toLowerCase();
        }
        if (canonicalSymbol.matches("^[A-Z0-9\\-]{1,10}$")) {
            return (canonicalSymbol.toLowerCase() + ".us").toLowerCase();
        }
        return null;
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

