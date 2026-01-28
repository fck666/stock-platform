package com.stock.platform.backend_api.api.dto;

import java.math.BigDecimal;

public record KdjDto(
        BigDecimal k,
        BigDecimal d,
        BigDecimal j
) {
}

