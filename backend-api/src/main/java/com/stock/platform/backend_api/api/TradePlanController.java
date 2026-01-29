package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.CreateTradePlanRequestDto;
import com.stock.platform.backend_api.api.dto.TradePlanDto;
import com.stock.platform.backend_api.api.dto.UpdateTradePlanRequestDto;
import com.stock.platform.backend_api.repository.MarketRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
public class TradePlanController {
    private static final String PROFILE_HEADER = "X-Profile-Key";
    private final MarketRepository market;

    public TradePlanController(MarketRepository market) {
        this.market = market;
    }

    @GetMapping
    public List<TradePlanDto> listPlans(
            @RequestHeader(PROFILE_HEADER) String profileKey,
            @RequestParam(required = false) String status
    ) {
        return market.listTradePlans(requireProfileKey(profileKey), status);
    }

    @PostMapping
    public TradePlanDto createPlan(
            @RequestHeader(PROFILE_HEADER) String profileKey,
            @RequestBody CreateTradePlanRequestDto req
    ) {
        return market.createTradePlan(requireProfileKey(profileKey), req);
    }

    @PutMapping("/{id}")
    public TradePlanDto updatePlan(
            @RequestHeader(PROFILE_HEADER) String profileKey,
            @PathVariable long id,
            @RequestBody UpdateTradePlanRequestDto req
    ) {
        return market.updateTradePlan(requireProfileKey(profileKey), id, req);
    }

    @DeleteMapping("/{id}")
    public void deletePlan(
            @RequestHeader(PROFILE_HEADER) String profileKey,
            @PathVariable long id
    ) {
        market.deleteTradePlan(requireProfileKey(profileKey), id);
    }

    private static String requireProfileKey(String v) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Missing profile key");
        }
        return v.trim();
    }
}

