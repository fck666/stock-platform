package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.FactorRankItemDto;
import com.stock.platform.backend_api.service.market.BarInterval;
import com.stock.platform.backend_api.service.market.FactorMetric;
import com.stock.platform.backend_api.service.market.FactorRankQuery;
import com.stock.platform.backend_api.service.market.MarketFactorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/market/factors")
public class MarketFactorsController {
    private final MarketFactorService factors;

    public MarketFactorsController(MarketFactorService factors) {
        this.factors = factors;
    }

    @GetMapping("/rank")
    public List<FactorRankItemDto> rank(
            @RequestParam(defaultValue = "^SPX") String index,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(defaultValue = "max_drawdown") String metric,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Integer lookback,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        return factors.rank(new FactorRankQuery(
                index,
                BarInterval.parse(interval),
                FactorMetric.parse(metric),
                mode,
                lookback,
                start,
                end,
                limit
        ));
    }
}

