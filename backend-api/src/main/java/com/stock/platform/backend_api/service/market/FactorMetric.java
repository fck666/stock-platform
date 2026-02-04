package com.stock.platform.backend_api.service.market;

public enum FactorMetric {
    MAX_DRAWDOWN,
    MAX_RUNUP,
    MAX_RUNDOWN,
    NEW_HIGH_COUNT,
    NEW_LOW_COUNT;

    public String value() {
        return switch (this) {
            case MAX_DRAWDOWN -> "max_drawdown";
            case MAX_RUNUP -> "max_runup";
            case MAX_RUNDOWN -> "max_rundown";
            case NEW_HIGH_COUNT -> "new_high_count";
            case NEW_LOW_COUNT -> "new_low_count";
        };
    }

    public static FactorMetric parse(String value) {
        if (value == null || value.isBlank()) {
            return MAX_DRAWDOWN;
        }
        return switch (value.trim().toLowerCase()) {
            case "max_drawdown", "drawdown", "mdd" -> MAX_DRAWDOWN;
            case "max_runup", "runup", "up_swing", "max_swing_up" -> MAX_RUNUP;
            case "max_rundown", "rundown", "down_swing", "max_swing_down" -> MAX_RUNDOWN;
            case "new_high_count", "new_high", "high_count" -> NEW_HIGH_COUNT;
            case "new_low_count", "new_low", "low_count" -> NEW_LOW_COUNT;
            default -> throw new IllegalArgumentException("Unsupported metric: " + value);
        };
    }
}

