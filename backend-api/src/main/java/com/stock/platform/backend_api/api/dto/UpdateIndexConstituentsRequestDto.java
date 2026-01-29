package com.stock.platform.backend_api.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateIndexConstituentsRequestDto(
        @NotNull List<String> stockSymbols
) {
}

