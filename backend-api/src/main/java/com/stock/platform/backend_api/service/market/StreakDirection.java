package com.stock.platform.backend_api.service.market;

public enum StreakDirection {
    UP(1),
    DOWN(-1);

    private final int sign;

    StreakDirection(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }

    public String value() {
        return this == UP ? "up" : "down";
    }

    public static StreakDirection parse(String value) {
        if (value == null || value.isBlank()) {
            return UP;
        }
        return switch (value.trim().toLowerCase()) {
            case "up", "rise", "rising", "inc", "increase" -> UP;
            case "down", "fall", "falling", "dec", "decrease" -> DOWN;
            default -> throw new IllegalArgumentException("Unsupported direction: " + value);
        };
    }
}

