package com.stock.platform.backend_api.api.dto;

import java.time.Instant;

public record SyncJobDto(
        String jobId,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Integer exitCode,
        String outputTail
) {
}

