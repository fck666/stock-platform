package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.AnalyticsSummaryDto;
import com.stock.platform.backend_api.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasAuthority('admin.analytics.read')")
/**
 * 管理端行为看板 API（聚合结果）。
 *
 * 对外仅暴露聚合数据，避免在业务系统内长期保留大量明细数据。
 */
public class AnalyticsAdminController {
    private final AnalyticsService analytics;

    public AnalyticsAdminController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryDto summary(@RequestParam(defaultValue = "14") int days) {
        return analytics.getSummary(days);
    }
}
