package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.SyncJobDto;
import com.stock.platform.backend_api.api.dto.SyncStocksRequest;
import com.stock.platform.backend_api.service.DataCollectorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private static final LocalDate DEFAULT_START = LocalDate.of(2016, 1, 1);

    private final DataCollectorService dataCollectorService;

    public SyncController(DataCollectorService dataCollectorService) {
        this.dataCollectorService = dataCollectorService;
    }

    @PostMapping("/sp500-index")
    public SyncJobDto syncSp500Index() {
        LocalDate end = LocalDate.now().minusDays(1);
        return dataCollectorService.startSyncIndices(DEFAULT_START, end);
    }

    @PostMapping("/stocks/{symbol}")
    public SyncJobDto syncSingleStock(@PathVariable String symbol) {
        LocalDate end = LocalDate.now().minusDays(1);
        return dataCollectorService.startSyncStock(symbol.toUpperCase(), DEFAULT_START, end);
    }

    @PostMapping("/stocks")
    public SyncJobDto syncStocks(@Valid @RequestBody SyncStocksRequest request) {
        LocalDate end = LocalDate.now().minusDays(1);
        List<String> symbols = request.symbols().stream().map(s -> s == null ? "" : s.trim().toUpperCase()).filter(s -> !s.isBlank()).toList();
        return dataCollectorService.startSyncStocks(symbols, DEFAULT_START, end);
    }

    @GetMapping("/jobs/{jobId}")
    public SyncJobDto getJob(@PathVariable String jobId) {
        return dataCollectorService.getJob(jobId);
    }
}

