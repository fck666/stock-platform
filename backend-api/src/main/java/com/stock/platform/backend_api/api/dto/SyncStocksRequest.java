package com.stock.platform.backend_api.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SyncStocksRequest(
        @NotEmpty
        @Size(max = 200)
        List<String> symbols
) {
}

