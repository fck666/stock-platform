package com.stock.platform.backend_api.service.market;

import java.time.LocalDate;

public record StreakQuery(
        String universeIndexSymbol,
        String stockSymbol,
        BarInterval interval,
        StreakDirection direction,
        LocalDate start,
        LocalDate end,
        Integer limit,
        Double volumeMultiple,
        Double flatThresholdPct
) {
}
