package com.stock.platform.backend_api.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AlertEventDto(
        long id,
        long ruleId,
        String symbol,
        String name,
        LocalDate barDate,
        String message,
        OffsetDateTime createdAt
) {
}

