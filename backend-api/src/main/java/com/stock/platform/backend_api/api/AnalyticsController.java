package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.PageViewRequestDto;
import com.stock.platform.backend_api.service.AnalyticsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
/**
 * 行为采集 API（业务侧）。
 *
 * 前端在路由切换后上报页面浏览，用于管理员看板聚合。
 */
public class AnalyticsController {
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @PostMapping("/page-view")
    @PreAuthorize("isAuthenticated()")
    public void pageView(@Valid @RequestBody PageViewRequestDto req) {
        analytics.recordPageView(req.path(), req.title());
    }
}
