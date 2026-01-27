package com.stock.platform.backend_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Cors cors,
        DataCollector dataCollector
) {
    public record Cors(String allowedOrigins) {
    }

    public record DataCollector(String workingDir, String pythonPath) {
    }
}

