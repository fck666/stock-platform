package com.stock.platform.backend_api.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AlertRuleDto(
        long id,
        String symbol,
        String name,
        String ruleType,
        boolean enabled,
        Double priceLevel,
        String priceDirection,
        Integer maPeriod,
        String maDirection,
        Double volumeMultiple,
        LocalDate lastTriggeredDate,
        OffsetDateTime updatedAt
) {
}

