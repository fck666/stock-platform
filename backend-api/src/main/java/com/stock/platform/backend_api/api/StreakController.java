package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.StreakRankItemDto;
import com.stock.platform.backend_api.service.market.BarInterval;
import com.stock.platform.backend_api.service.market.StreakDirection;
import com.stock.platform.backend_api.service.market.StreakQuery;
import com.stock.platform.backend_api.service.market.StreakService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/market/streaks")
public class StreakController {
    private final StreakService streaks;

    public StreakController(StreakService streaks) {
        this.streaks = streaks;
    }

    @GetMapping("/rank")
    public List<StreakRankItemDto> rank(
            @RequestParam(defaultValue = "^SPX") String index,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(defaultValue = "up") String direction,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(required = false) Double volumeMultiple,
            @RequestParam(required = false) Double flatThresholdPct
    ) {
        return streaks.rank(new StreakQuery(
                index,
                null,
                BarInterval.parse(interval),
                StreakDirection.parse(direction),
                start,
                end,
                limit,
                volumeMultiple,
                flatThresholdPct
        ));
    }

    @GetMapping("/symbols/{symbol}/longest")
    public StreakRankItemDto longestForSymbol(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(defaultValue = "up") String direction,
            @RequestParam(required = false) LocalDate start,
            @RequestParam(required = false) LocalDate end,
            @RequestParam(required = false) Double volumeMultiple,
            @RequestParam(required = false) Double flatThresholdPct
    ) {
        return streaks.longestForSymbol(new StreakQuery(
                null,
                symbol,
                BarInterval.parse(interval),
                StreakDirection.parse(direction),
                start,
                end,
                null,
                volumeMultiple,
                flatThresholdPct
        ));
    }
}
