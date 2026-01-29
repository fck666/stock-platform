package com.stock.platform.backend_api.api.dto;

public record RsPointDto(
        String date,
        Double stockClose,
        Double indexClose,
        Double rs,
        Double rsNormalized
) {
}

