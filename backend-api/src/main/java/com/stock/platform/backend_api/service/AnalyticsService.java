package com.stock.platform.backend_api.service;

import com.stock.platform.backend_api.api.dto.AnalyticsSummaryDto;
import com.stock.platform.backend_api.repository.AnalyticsRepository;
import com.stock.platform.backend_api.security.AuthUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
/**
 * 轻量行为采集与看板聚合服务。
 *
 * - recordPageView：由前端上报页面浏览（仅登录用户）
 * - recordApiCall：由后端过滤器自动采集 API 调用
 * - getSummary：管理端聚合查询（按天趋势 + Top 列表）
 */
public class AnalyticsService {
    private final AnalyticsRepository analytics;

    public AnalyticsService(AnalyticsRepository analytics) {
        this.analytics = analytics;
    }

    public void recordPageView(String path, String title) {
        AuthUser u = currentUserOrThrow();
        analytics.insertPageView(u.userId(), u.username(), path, title, Instant.now());
    }

    public AnalyticsSummaryDto getSummary(int days) {
        int window = Math.min(Math.max(days, 1), 90);
        return new AnalyticsSummaryDto(
                analytics.getDailyPageViews(window),
                analytics.getDailyApiCalls(window),
                analytics.getTopPages(window, 12),
                analytics.getTopApis(window, 12),
                analytics.getTopUsersByApiCalls(window, 12)
        );
    }

    public void recordApiCall(UUID userId, String username, String method, String path, int statusCode, int latencyMs, Instant now) {
        analytics.insertApiCall(userId, username, method, path, statusCode, latencyMs, now);
    }

    private static AuthUser currentUserOrThrow() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthUser u) return u;
        throw new AccessDeniedException("Not authenticated");
    }
}
