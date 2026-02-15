package com.stock.platform.backend_api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 每个请求的上下文信息（ThreadLocal 存放）。
 *
 * route() 会优先使用 Spring MVC 解析后的 BEST_MATCHING_PATTERN（例如 /api/admin/users/{id}），
 * 便于聚合统计与审计查询；若不可用则回退为原始 path。
 */
public record RequestContext(
        String requestId,
        String ipAddress,
        String userAgent,
        String httpMethod,
        String path,
        long startNanos,
        HttpServletRequest request
) {
    public String route() {
        if (request == null) return path;
        Object v = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (v instanceof String s && !s.isBlank()) return s;
        return path;
    }
}
