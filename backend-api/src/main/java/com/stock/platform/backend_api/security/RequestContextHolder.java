package com.stock.platform.backend_api.security;

import java.util.Optional;

/**
 * 请求上下文 ThreadLocal。
 *
 * 注意：必须在过滤器链结束时 clear，避免线程复用导致数据串扰。
 */
public final class RequestContextHolder {
    private static final ThreadLocal<RequestContext> CTX = new ThreadLocal<>();

    private RequestContextHolder() {
    }

    public static void set(RequestContext ctx) {
        CTX.set(ctx);
    }

    public static Optional<RequestContext> get() {
        return Optional.ofNullable(CTX.get());
    }

    public static void clear() {
        CTX.remove();
    }
}
