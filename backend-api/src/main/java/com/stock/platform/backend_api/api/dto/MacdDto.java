package com.stock.platform.backend_api.api.dto;

import java.math.BigDecimal;

public record MacdDto(
        BigDecimal dif,
        BigDecimal dea,
        BigDecimal hist
) {
}

