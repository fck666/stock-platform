package com.stock.platform.backend_api.service.market;

public enum BarInterval {
    D1("1d"),
    W1("1w"),
    M1("1m");

    private final String value;

    BarInterval(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static BarInterval parse(String value) {
        if (value == null || value.isBlank()) {
            return D1;
        }
        return switch (value.trim().toLowerCase()) {
            case "1d", "d", "day", "daily" -> D1;
            case "1w", "w", "week", "weekly" -> W1;
            case "1m", "m", "month", "monthly" -> M1;
            default -> throw new IllegalArgumentException("Unsupported interval: " + value);
        };
    }
}

