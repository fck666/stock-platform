package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.*;
import com.stock.platform.backend_api.repository.MarketRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
    private static final String PROFILE_HEADER = "X-Profile-Key";
    private final MarketRepository market;

    public AlertController(MarketRepository market) {
        this.market = market;
    }

    @GetMapping("/rules")
    public List<AlertRuleDto> listRules(@RequestHeader(PROFILE_HEADER) String profileKey) {
        return market.listAlertRules(requireProfileKey(profileKey));
    }

    @PostMapping("/rules")
    public AlertRuleDto createRule(
            @RequestHeader(PROFILE_HEADER) String profileKey,
            @RequestBody CreateAlertRuleRequestDto req
    ) {
        return market.createAlertRule(requireProfileKey(profileKey), req);
    }

    @PutMapping("/rules/{id}")
    public AlertRuleDto updateRule(
            @RequestHeader(PROFILE_HEADER) String profileKey,
            @PathVariable long id,
            @RequestBody UpdateAlertRuleRequestDto req
    ) {
        return market.updateAlertRule(requireProfileKey(profileKey), id, req);
    }

    @DeleteMapping("/rules/{id}")
    public void deleteRule(
            @RequestHeader(PROFILE_HEADER) String profileKey,
            @PathVariable long id
    ) {
        market.deleteAlertRule(requireProfileKey(profileKey), id);
    }

    @GetMapping("/events")
    public List<AlertEventDto> listEvents(
            @RequestHeader(PROFILE_HEADER) String profileKey,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return market.listAlertEvents(requireProfileKey(profileKey), limit);
    }

    @PostMapping("/evaluate")
    public EvaluateAlertsResponseDto evaluate(
            @RequestHeader(PROFILE_HEADER) String profileKey,
            @RequestParam(defaultValue = "50") int latestLimit
    ) {
        return market.evaluateAlerts(requireProfileKey(profileKey), latestLimit);
    }

    private static String requireProfileKey(String v) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing profile key");
        }
        return v.trim();
    }
}

