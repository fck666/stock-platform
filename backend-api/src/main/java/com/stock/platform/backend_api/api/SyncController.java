package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.SyncJobDto;
import com.stock.platform.backend_api.api.dto.SyncStocksRequest;
import com.stock.platform.backend_api.service.DataCollectorService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sync")
@PreAuthorize("hasAuthority('data.sync.execute')")
public class SyncController {
    private static final LocalDate DEFAULT_START = LocalDate.of(2016, 1, 1);

    private final DataCollectorService dataCollectorService;

    public SyncController(DataCollectorService dataCollectorService) {
        this.dataCollectorService = dataCollectorService;
    }

    @PostMapping("/sp500-index")
    public SyncJobDto syncSp500Index() {
        LocalDate end = LocalDate.now().minusDays(1);
        return dataCollectorService.startSyncIndices("^SPX", DEFAULT_START, end);
    }

    @PostMapping("/wiki")
    public SyncJobDto syncWiki(@RequestParam(defaultValue = "^SPX") String index) {
        return dataCollectorService.startSyncWiki(index.toUpperCase());
    }

    @PostMapping("/fundamentals")
    public SyncJobDto syncFundamentals(@RequestParam(defaultValue = "^SPX") String index) {
        return dataCollectorService.startSyncFundamentals(index.toUpperCase());
    }

    @PostMapping("/prices")
    public SyncJobDto syncPrices(@RequestParam(defaultValue = "^SPX") String index) {
        String indexSymbol = index.toUpperCase();
        int daysAgo = "^HSI".equals(indexSymbol) ? 2 : 1;
        LocalDate end = LocalDate.now().minusDays(daysAgo);
        return dataCollectorService.startSyncIndexPrices(indexSymbol, DEFAULT_START, end);
    }

    @PostMapping("/stocks/{symbol}")
    public SyncJobDto syncSingleStock(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "^SPX") String index
    ) {
        String indexSymbol = index.toUpperCase();
        int daysAgo = "^HSI".equals(indexSymbol) ? 2 : 1;
        LocalDate end = LocalDate.now().minusDays(daysAgo);
        return dataCollectorService.startSyncStock(indexSymbol, symbol.toUpperCase(), DEFAULT_START, end);
    }

    @PostMapping("/stocks")
    public SyncJobDto syncStocks(
            @Valid @RequestBody SyncStocksRequest request,
            @RequestParam(defaultValue = "^SPX") String index
    ) {
        String indexSymbol = index.toUpperCase();
        int daysAgo = "^HSI".equals(indexSymbol) ? 2 : 1;
        LocalDate end = LocalDate.now().minusDays(daysAgo);
        List<String> symbols = request.symbols().stream()
                .map(s -> s == null ? "" : s.trim().toUpperCase())
                .filter(s -> !s.isBlank())
                .toList();
        return dataCollectorService.startSyncStocks(indexSymbol, symbols, DEFAULT_START, end);
    }

    @GetMapping("/jobs/{jobId}")
    public SyncJobDto getJob(@PathVariable String jobId) {
        return dataCollectorService.getJob(jobId);
    }
}
