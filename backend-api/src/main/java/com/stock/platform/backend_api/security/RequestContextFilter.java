package com.stock.platform.backend_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import com.stock.platform.backend_api.service.AnalyticsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
/**
 * RequestContextFilter
 *
 * - 为每个请求生成/透传 X-Request-Id，并写回响应头
 * - 采集 IP / User-Agent / method / path / 开始时间，并存入 ThreadLocal 供审计使用
 * - 对已登录用户进行轻量 API 调用采集（聚合看板用），避免记录认证/看板相关接口以减少噪声与循环上报
 */
public class RequestContextFilter extends OncePerRequestFilter {
    private final AnalyticsService analytics;

    public RequestContextFilter(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = getOrCreateRequestId(request);
        response.setHeader("X-Request-Id", requestId);

        String ip = extractClientIp(request);
        String ua = request.getHeader("User-Agent");
        String method = request.getMethod();
        String path = request.getRequestURI();
        long startNanos = System.nanoTime();

        RequestContext ctx = new RequestContext(
                requestId,
                ip,
                ua,
                method,
                path,
                startNanos,
                request
        );
        RequestContextHolder.set(ctx);
        try {
            filterChain.doFilter(request, response);
        } finally {
            recordApiCallIfNeeded(request, response, ctx);
            RequestContextHolder.clear();
        }
    }

    /**
     * 仅记录已登录用户的 API 调用（避免匿名流量导致数据膨胀）。
     */
    private void recordApiCallIfNeeded(HttpServletRequest request, HttpServletResponse response, RequestContext ctx) {
        if (!shouldRecordApiCall(request)) return;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser u)) return;

        int status = response.getStatus();
        int latencyMs = (int) Math.max(0, Math.min(3_600_000L, (System.nanoTime() - ctx.startNanos()) / 1_000_000L));
        analytics.recordApiCall(u.userId(), u.username(), ctx.httpMethod(), ctx.route(), status, latencyMs, Instant.now());
    }

    /**
     * 只采集 /api/**，且排除认证、看板自身等路径。
     */
    private static boolean shouldRecordApiCall(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) return false;
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return false;
        if (uri.startsWith("/api/auth/")) return false;
        if (uri.equals("/api/me")) return false;
        if (uri.startsWith("/api/analytics/")) return false;
        if (uri.startsWith("/api/admin/analytics/")) return false;
        return true;
    }

    private static String getOrCreateRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        if (header != null && !header.isBlank()) return header.trim();
        return UUID.randomUUID().toString();
    }

    private static String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) return first;
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }
}
