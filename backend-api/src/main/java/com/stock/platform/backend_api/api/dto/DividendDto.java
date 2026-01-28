package com.stock.platform.backend_api.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DividendDto(
        LocalDate exDate,
        BigDecimal amount,
        String type,
        String rawText
) {
}
