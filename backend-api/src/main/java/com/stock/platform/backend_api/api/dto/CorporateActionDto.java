package com.stock.platform.backend_api.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CorporateActionDto(
        LocalDate exDate,
        String actionType,
        BigDecimal cashAmount,
        String currency,
        Integer splitNumerator,
        Integer splitDenominator,
        String source
) {
}

