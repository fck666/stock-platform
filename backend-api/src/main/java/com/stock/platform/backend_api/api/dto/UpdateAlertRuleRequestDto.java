package com.stock.platform.backend_api.api.dto;

public record UpdateAlertRuleRequestDto(
        boolean enabled,
        Double priceLevel,
        String priceDirection,
        Integer maPeriod,
        String maDirection,
        Double volumeMultiple
) {
}

