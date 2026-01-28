package com.stock.platform.backend_api.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record IndicatorPointDto(
        LocalDate date,
        Map<Integer, BigDecimal> ma,
        MacdDto macd,
        KdjDto kdj
) {
}

