package com.stock.platform.backend_api.service.market;

import java.time.LocalDate;

public record FactorRankQuery(
        String universeIndexSymbol,
        BarInterval interval,
        FactorMetric metric,
        String mode,
        Integer lookback,
        LocalDate start,
        LocalDate end,
        Integer limit
) {
}

