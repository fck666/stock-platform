package com.stock.platform.backend_api.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        UUID actorId,
        String actorUsername,
        UUID targetId,
        String targetUsername,
        String action,
        String details,
        String ipAddress,
        String userAgent,
        String requestId,
        String httpMethod,
        String route,
        Integer statusCode,
        Integer latencyMs,
        Instant createdAt
) {}
