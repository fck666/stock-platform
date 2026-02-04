package com.stock.platform.backend_api.repository;

import com.stock.platform.backend_api.api.dto.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
public class MarketRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public MarketRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LocalDate> getLatestIndexAsOfDate(String indexSymbol) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("symbol", indexSymbol);
        LocalDate asOf = jdbc.query(
                """
                select max(m.as_of_date) as as_of_date 
                from market.index_membership m
                join market.security s on s.id = m.index_id
                where s.canonical_symbol = :symbol
                """,
                params,
                rs -> rs.next() ? rs.getObject("as_of_date", LocalDate.class) : null
        );
        return Optional.ofNullable(asOf);
    }

    public List<StockListItemDto> getAllIndexStocks(String indexSymbol) {
        boolean listAll = indexSymbol == null || indexSymbol.isBlank() || "ALL".equalsIgnoreCase(indexSymbol);
        LocalDate asOf = null;
        if (!listAll) {
            asOf = getLatestIndexAsOfDate(indexSymbol).orElse(null);
            if (asOf == null) {
                return List.of();
            }
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (!listAll) {
            params.addValue("indexSymbol", indexSymbol);
            params.addValue("asOf", asOf);
        }

        String sql;
        if (listAll) {
            sql = """
                select
                    s.canonical_symbol as symbol,
                    s.name as name,
                    sd.sector,
                    sd.sub_industry,
                    sd.headquarters,
                    null as wiki_description
                from market.security s
                left join market.security_detail sd on sd.security_id = s.id
                where s.security_type = 'STOCK'
                """;
        } else {
            sql = """
                select
                    s.canonical_symbol as symbol,
                    s.name as name,
                    sd.sector,
                    sd.sub_industry,
                    sd.headquarters,
                    null as wiki_description
                from market.security s
                join market.index_membership m on m.security_id = s.id and m.as_of_date = :asOf
                join market.security idx on idx.id = m.index_id and idx.canonical_symbol = :indexSymbol
                left join market.security_detail sd on sd.security_id = s.id
                where s.security_type = 'STOCK'
                """;
        }

        return jdbc.query(sql, params, (rs, rowNum) -> new StockListItemDto(
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getString("sector"),
                rs.getString("sub_industry"),
                rs.getString("headquarters"),
                rs.getString("wiki_description")
        ));
    }

    public PagedResponse<StockListItemDto> listIndexStocks(
            String indexSymbol,
            String query,
            int page,
            int size,
            String sortBy,
            String sortDir,
            String lang
    ) {
        boolean listAll = indexSymbol == null || indexSymbol.isBlank() || "ALL".equalsIgnoreCase(indexSymbol);
        LocalDate asOf = null;
        if (!listAll) {
            asOf = getLatestIndexAsOfDate(indexSymbol).orElse(null);
            if (asOf == null) {
                return new PagedResponse<>(List.of(), 0, page, size);
            }
        }

        String q = query == null ? null : query.trim();
        boolean hasQuery = q != null && !q.isBlank();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lang", lang)
                .addValue("limit", size)
                .addValue("offset", Math.max(0, page) * size);
        if (!listAll) {
            params.addValue("indexSymbol", indexSymbol);
            params.addValue("asOf", asOf);
        }

        if (hasQuery) {
            params.addValue("qLike", "%" + q + "%");
        }

        String where = hasQuery
                ? "and (s.canonical_symbol ilike :qLike or s.name ilike :qLike or ws.title ilike :qLike or ws.description ilike :qLike)"
                : "";

        String orderCol = "s.canonical_symbol";
        String sortKey = sortBy == null ? "" : sortBy.trim().toLowerCase(Locale.ROOT);
        if (sortKey.equals("name")) {
            orderCol = "coalesce(s.name, '')";
        } else if (sortKey.equals("symbol")) {
            orderCol = "s.canonical_symbol";
        }
        String dir = sortDir == null ? "" : sortDir.trim().toLowerCase(Locale.ROOT);
        String orderDir = dir.equals("desc") ? "desc" : "asc";
        String orderBy = orderCol.equals("s.canonical_symbol")
                ? "order by s.canonical_symbol " + orderDir
                : "order by " + orderCol + " " + orderDir + ", s.canonical_symbol asc";

        long total;
        if (listAll) {
            total = jdbc.queryForObject(
                    """
                    select count(*) as cnt
                    from market.security s
                    left join market.wiki_summary ws on ws.security_id = s.id and ws.lang = :lang
                    where s.security_type = 'STOCK'
                    %s
                    """.formatted(where),
                    params,
                    Long.class
            );
        } else {
            total = jdbc.queryForObject(
                    """
                    select count(*) as cnt
                    from market.security s
                    join market.index_membership m on m.security_id = s.id and m.as_of_date = :asOf
                    join market.security idx on idx.id = m.index_id and idx.canonical_symbol = :indexSymbol
                    left join market.wiki_summary ws on ws.security_id = s.id and ws.lang = :lang
                    where s.security_type = 'STOCK'
                    %s
                    """.formatted(where),
                    params,
                    Long.class
            );
        }

        List<StockListItemDto> items;
        if (listAll) {
            items = jdbc.query(
                    """
                    select
                        s.canonical_symbol as symbol,
                        s.name as name,
                        sd.sector,
                        sd.sub_industry,
                        sd.headquarters,
                        ws.description as wiki_description
                    from market.security s
                    left join market.security_detail sd on sd.security_id = s.id
                    left join market.wiki_summary ws on ws.security_id = s.id and ws.lang = :lang
                    where s.security_type = 'STOCK'
                    %s
                    %s
                    limit :limit offset :offset
                    """.formatted(where, orderBy),
                    params,
                    (rs, rowNum) -> new StockListItemDto(
                            rs.getString("symbol"),
                            rs.getString("name"),
                            rs.getString("sector"),
                            rs.getString("sub_industry"),
                            rs.getString("headquarters"),
                            rs.getString("wiki_description")
                    )
            );
        } else {
            items = jdbc.query(
                    """
                    select
                        s.canonical_symbol as symbol,
                        s.name as name,
                        sd.sector,
                        sd.sub_industry,
                        sd.headquarters,
                        ws.description as wiki_description
                    from market.security s
                    join market.index_membership m on m.security_id = s.id and m.as_of_date = :asOf
                    join market.security idx on idx.id = m.index_id and idx.canonical_symbol = :indexSymbol
                    left join market.security_detail sd on sd.security_id = s.id
                    left join market.wiki_summary ws on ws.security_id = s.id and ws.lang = :lang
                    where s.security_type = 'STOCK'
                    %s
                    %s
                    limit :limit offset :offset
                    """.formatted(where, orderBy),
                    params,
                    (rs, rowNum) -> new StockListItemDto(
                            rs.getString("symbol"),
                            rs.getString("name"),
                            rs.getString("sector"),
                            rs.getString("sub_industry"),
                            rs.getString("headquarters"),
                            rs.getString("wiki_description")
                    )
            );
        }

        return new PagedResponse<>(items, total, page, size);
    }

    public PagedResponse<StockListItemDto> listSp500Stocks(
            String query,
            int page,
            int size,
            String lang
    ) {
        return listIndexStocks("^SPX", query, page, size, null, null, lang);
    }

    public Optional<Long> findSecurityIdBySymbol(String canonicalSymbol) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("symbol", canonicalSymbol);
        List<Long> ids = jdbc.query(
                "select id from market.security where canonical_symbol = :symbol",
                params,
                (rs, rowNum) -> rs.getLong("id")
        );
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    public List<IndexListItemDto> listIndices() {
        return jdbc.query(
                """
                select s.canonical_symbol as symbol, s.name as name, sd.wiki_url as wiki_url
                from market.security s
                left join market.security_detail sd on sd.security_id = s.id
                where s.security_type = 'INDEX'
                order by s.canonical_symbol asc
                """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new IndexListItemDto(
                        rs.getString("symbol"),
                        rs.getString("name"),
                        rs.getString("wiki_url")
                )
        );
    }

    public long createSecurity(String securityType, String canonicalSymbol, String name, String wikiUrl, String stooqSymbol) {
        MapSqlParameterSource checkParams = new MapSqlParameterSource().addValue("symbol", canonicalSymbol);
        List<String> existingTypes = jdbc.query(
                "select security_type from market.security where canonical_symbol = :symbol",
                checkParams,
                (rs, rowNum) -> rs.getString("security_type")
        );
        if (!existingTypes.isEmpty()) {
            throw new IllegalStateException("Symbol already exists: " + canonicalSymbol);
        }

        Long id = jdbc.queryForObject(
                """
                insert into market.security (security_type, canonical_symbol, name)
                values (:type, :symbol, :name)
                returning id
                """,
                new MapSqlParameterSource()
                        .addValue("type", securityType)
                        .addValue("symbol", canonicalSymbol)
                        .addValue("name", name),
                Long.class
        );
        if (id == null) {
            throw new IllegalStateException("Failed to create security: " + canonicalSymbol);
        }

        if (wikiUrl != null || stooqSymbol != null) {
            jdbc.update(
                    """
                    insert into market.security_detail (security_id, wiki_url, stooq_symbol)
                    values (:id, :wikiUrl, :stooqSymbol)
                    on conflict (security_id) do update set
                        wiki_url = coalesce(excluded.wiki_url, market.security_detail.wiki_url),
                        stooq_symbol = coalesce(excluded.stooq_symbol, market.security_detail.stooq_symbol),
                        updated_at = now()
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("wikiUrl", wikiUrl)
                            .addValue("stooqSymbol", stooqSymbol)
            );
        }

        if (stooqSymbol != null) {
            MapSqlParameterSource idParams = new MapSqlParameterSource()
                    .addValue("provider", "stooq")
                    .addValue("identifier", stooqSymbol);
            List<Long> existing = jdbc.query(
                    "select security_id from market.security_identifier where provider = :provider and identifier = :identifier",
                    idParams,
                    (rs, rowNum) -> rs.getLong("security_id")
            );
            if (!existing.isEmpty() && existing.get(0) != id) {
                throw new IllegalStateException("Identifier already exists: stooq=" + stooqSymbol);
            }
            if (existing.isEmpty()) {
                jdbc.update(
                        """
                        insert into market.security_identifier (security_id, provider, identifier, is_primary)
                        values (:id, :provider, :identifier, true)
                        """,
                        idParams.addValue("id", id)
                );
            }
        }

        return id;
    }

    public long requireIndexId(String indexSymbol) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("symbol", indexSymbol);
        List<Long> ids = jdbc.query(
                "select id from market.security where security_type = 'INDEX' and canonical_symbol = :symbol",
                params,
                (rs, rowNum) -> rs.getLong("id")
        );
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Index not found: " + indexSymbol);
        }
        return ids.get(0);
    }

    public Map<String, Long> resolveStockIds(List<String> canonicalSymbols) {
        if (canonicalSymbols == null || canonicalSymbols.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("symbols", canonicalSymbols);
        List<Map.Entry<String, Long>> rows = jdbc.query(
                """
                select canonical_symbol, id
                from market.security
                where security_type = 'STOCK' and canonical_symbol in (:symbols)
                """,
                params,
                (rs, rowNum) -> Map.entry(rs.getString("canonical_symbol"), rs.getLong("id"))
        );
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, Long> e : rows) {
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    public long requireProfileId(String profileKey) {
        if (profileKey == null || profileKey.isBlank()) {
            throw new IllegalArgumentException("Missing profile key");
        }
        String key = profileKey.trim();
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("key", key);
        jdbc.update(
                """
                insert into market.profile (profile_key)
                values (:key)
                on conflict (profile_key) do nothing
                """,
                params
        );
        Long id = jdbc.queryForObject(
                "select id from market.profile where profile_key = :key",
                params,
                Long.class
        );
        if (id == null) {
            throw new IllegalStateException("Failed to resolve profile");
        }
        return id;
    }

    public long requireStockId(String canonicalSymbol) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("symbol", canonicalSymbol);
        List<Long> ids = jdbc.query(
                "select id from market.security where security_type = 'STOCK' and canonical_symbol = :symbol",
                params,
                (rs, rowNum) -> rs.getLong("id")
        );
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Stock not found: " + canonicalSymbol);
        }
        return ids.get(0);
    }

    public List<TradePlanDto> listTradePlans(String profileKey, String status) {
        long profileId = requireProfileId(profileKey);
        String st = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profileId", profileId)
                .addValue("status", st);
        record Row(
                long id,
                String symbol,
                String name,
                String direction,
                String status,
                LocalDate startDate,
                BigDecimal entryPrice,
                BigDecimal entryLow,
                BigDecimal entryHigh,
                BigDecimal stopPrice,
                BigDecimal targetPrice,
                String note,
                LocalDate lastBarDate,
                BigDecimal lastClose,
                BigDecimal minLow,
                BigDecimal maxHigh,
                java.time.OffsetDateTime updatedAt
        ) {}

        List<Row> rows = jdbc.query(
                """
                select
                    tp.id,
                    s.canonical_symbol as symbol,
                    s.name as name,
                    tp.direction,
                    tp.status,
                    tp.start_date,
                    tp.entry_price,
                    tp.entry_low,
                    tp.entry_high,
                    tp.stop_price,
                    tp.target_price,
                    tp.note,
                    lb.bar_date as last_bar_date,
                    lb.close as last_close,
                    stats.min_low,
                    stats.max_high,
                    tp.updated_at
                from market.trade_plan tp
                join market.security s on s.id = tp.security_id
                left join lateral (
                    select bar_date, close
                    from market.price_bar
                    where security_id = tp.security_id and interval = '1d'
                    order by bar_date desc
                    limit 1
                ) lb on true
                left join lateral (
                    select min(low) as min_low, max(high) as max_high
                    from market.price_bar
                    where security_id = tp.security_id
                      and interval = '1d'
                      and bar_date between tp.start_date and coalesce(lb.bar_date, tp.start_date)
                ) stats on true
                where tp.profile_id = :profileId
                  and (:status is null or tp.status = :status)
                order by tp.updated_at desc
                """,
                params,
                (rs, rowNum) -> new Row(
                        rs.getLong("id"),
                        rs.getString("symbol"),
                        rs.getString("name"),
                        rs.getString("direction"),
                        rs.getString("status"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getBigDecimal("entry_price"),
                        rs.getBigDecimal("entry_low"),
                        rs.getBigDecimal("entry_high"),
                        rs.getBigDecimal("stop_price"),
                        rs.getBigDecimal("target_price"),
                        rs.getString("note"),
                        rs.getObject("last_bar_date", LocalDate.class),
                        rs.getBigDecimal("last_close"),
                        rs.getBigDecimal("min_low"),
                        rs.getBigDecimal("max_high"),
                        rs.getObject("updated_at", java.time.OffsetDateTime.class)
                )
        );

        return rows.stream().map(r -> {
            Double entry = r.entryPrice() == null ? null : r.entryPrice().doubleValue();
            Double last = r.lastClose() == null ? null : r.lastClose().doubleValue();
            Double pnlPct = null;
            if (entry != null && entry != 0 && last != null) {
                if ("SHORT".equalsIgnoreCase(r.direction())) {
                    pnlPct = (entry / last) - 1.0;
                } else {
                    pnlPct = (last / entry) - 1.0;
                }
            }

            Boolean hitStop = null;
            Boolean hitTarget = null;
            if (entry != null && entry != 0) {
                if ("SHORT".equalsIgnoreCase(r.direction())) {
                    if (r.stopPrice() != null && r.maxHigh() != null) {
                        hitStop = r.maxHigh().compareTo(r.stopPrice()) >= 0;
                    }
                    if (r.targetPrice() != null && r.minLow() != null) {
                        hitTarget = r.minLow().compareTo(r.targetPrice()) <= 0;
                    }
                } else {
                    if (r.stopPrice() != null && r.minLow() != null) {
                        hitStop = r.minLow().compareTo(r.stopPrice()) <= 0;
                    }
                    if (r.targetPrice() != null && r.maxHigh() != null) {
                        hitTarget = r.maxHigh().compareTo(r.targetPrice()) >= 0;
                    }
                }
            }

            return new TradePlanDto(
                    r.id(),
                    r.symbol(),
                    r.name(),
                    r.direction(),
                    r.status(),
                    r.startDate(),
                    entry,
                    r.entryLow() == null ? null : r.entryLow().doubleValue(),
                    r.entryHigh() == null ? null : r.entryHigh().doubleValue(),
                    r.stopPrice() == null ? null : r.stopPrice().doubleValue(),
                    r.targetPrice() == null ? null : r.targetPrice().doubleValue(),
                    r.note(),
                    r.lastBarDate(),
                    last,
                    pnlPct,
                    hitStop,
                    hitTarget,
                    r.updatedAt()
            );
        }).toList();
    }

    public TradePlanDto createTradePlan(String profileKey, CreateTradePlanRequestDto req) {
        if (req == null) throw new IllegalArgumentException("Request is required");
        long profileId = requireProfileId(profileKey);
        String symbol = req.symbol() == null ? null : req.symbol().trim().toUpperCase(Locale.ROOT);
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        long stockId = requireStockId(symbol);

        String direction = req.direction() == null || req.direction().isBlank() ? "LONG" : req.direction().trim().toUpperCase(Locale.ROOT);
        if (!direction.equals("LONG") && !direction.equals("SHORT")) {
            throw new IllegalArgumentException("Invalid direction: " + direction);
        }
        String status = req.status() == null || req.status().isBlank() ? "PLANNED" : req.status().trim().toUpperCase(Locale.ROOT);
        if (!List.of("PLANNED", "OPEN", "CLOSED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
        LocalDate startDate = req.startDate() == null ? LocalDate.now() : req.startDate();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profileId", profileId)
                .addValue("securityId", stockId)
                .addValue("direction", direction)
                .addValue("status", status)
                .addValue("startDate", startDate)
                .addValue("entryPrice", req.entryPrice())
                .addValue("entryLow", req.entryLow())
                .addValue("entryHigh", req.entryHigh())
                .addValue("stopPrice", req.stopPrice())
                .addValue("targetPrice", req.targetPrice())
                .addValue("note", req.note());

        Long id = jdbc.queryForObject(
                """
                insert into market.trade_plan (
                    profile_id, security_id, direction, status, start_date,
                    entry_price, entry_low, entry_high, stop_price, target_price, note
                )
                values (
                    :profileId, :securityId, :direction, :status, :startDate,
                    :entryPrice, :entryLow, :entryHigh, :stopPrice, :targetPrice, :note
                )
                returning id
                """,
                params,
                Long.class
        );
        if (id == null) throw new IllegalStateException("Failed to create plan");
        return listTradePlans(profileKey, null).stream().filter(p -> p.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to load created plan"));
    }

    public TradePlanDto updateTradePlan(String profileKey, long id, UpdateTradePlanRequestDto req) {
        if (req == null) throw new IllegalArgumentException("Request is required");
        long profileId = requireProfileId(profileKey);
        String direction = req.direction() == null ? null : req.direction().trim().toUpperCase(Locale.ROOT);
        if (direction != null && !direction.isBlank() && !direction.equals("LONG") && !direction.equals("SHORT")) {
            throw new IllegalArgumentException("Invalid direction: " + direction);
        }
        String status = req.status() == null ? null : req.status().trim().toUpperCase(Locale.ROOT);
        if (status != null && !status.isBlank() && !List.of("PLANNED", "OPEN", "CLOSED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("profileId", profileId)
                .addValue("direction", direction == null || direction.isBlank() ? null : direction)
                .addValue("status", status == null || status.isBlank() ? null : status)
                .addValue("startDate", req.startDate())
                .addValue("entryPrice", req.entryPrice())
                .addValue("entryLow", req.entryLow())
                .addValue("entryHigh", req.entryHigh())
                .addValue("stopPrice", req.stopPrice())
                .addValue("targetPrice", req.targetPrice())
                .addValue("note", req.note());

        int updated = jdbc.update(
                """
                update market.trade_plan
                set
                    direction = coalesce(:direction, direction),
                    status = coalesce(:status, status),
                    start_date = coalesce(:startDate, start_date),
                    entry_price = coalesce(:entryPrice, entry_price),
                    entry_low = coalesce(:entryLow, entry_low),
                    entry_high = coalesce(:entryHigh, entry_high),
                    stop_price = coalesce(:stopPrice, stop_price),
                    target_price = coalesce(:targetPrice, target_price),
                    note = coalesce(:note, note),
                    updated_at = now()
                where id = :id and profile_id = :profileId
                """,
                params
        );
        if (updated == 0) throw new IllegalArgumentException("Plan not found: " + id);
        return listTradePlans(profileKey, null).stream().filter(p -> p.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to load updated plan"));
    }

    public void deleteTradePlan(String profileKey, long id) {
        long profileId = requireProfileId(profileKey);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id).addValue("profileId", profileId);
        int deleted = jdbc.update("delete from market.trade_plan where id = :id and profile_id = :profileId", params);
        if (deleted == 0) throw new IllegalArgumentException("Plan not found: " + id);
    }

    public List<AlertRuleDto> listAlertRules(String profileKey) {
        long profileId = requireProfileId(profileKey);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("profileId", profileId);
        return jdbc.query(
                """
                select
                    r.id,
                    s.canonical_symbol as symbol,
                    s.name as name,
                    r.rule_type,
                    r.enabled,
                    r.price_level,
                    r.price_direction,
                    r.ma_period,
                    r.ma_direction,
                    r.volume_multiple,
                    r.last_triggered_date,
                    r.updated_at
                from market.alert_rule r
                join market.security s on s.id = r.security_id
                where r.profile_id = :profileId
                order by r.updated_at desc
                """,
                params,
                (rs, rowNum) -> new AlertRuleDto(
                        rs.getLong("id"),
                        rs.getString("symbol"),
                        rs.getString("name"),
                        rs.getString("rule_type"),
                        rs.getBoolean("enabled"),
                        rs.getBigDecimal("price_level") == null ? null : rs.getBigDecimal("price_level").doubleValue(),
                        rs.getString("price_direction"),
                        rs.getObject("ma_period", Integer.class),
                        rs.getString("ma_direction"),
                        rs.getBigDecimal("volume_multiple") == null ? null : rs.getBigDecimal("volume_multiple").doubleValue(),
                        rs.getObject("last_triggered_date", LocalDate.class),
                        rs.getObject("updated_at", java.time.OffsetDateTime.class)
                )
        );
    }

    public AlertRuleDto createAlertRule(String profileKey, CreateAlertRuleRequestDto req) {
        if (req == null) throw new IllegalArgumentException("Request is required");
        long profileId = requireProfileId(profileKey);
        String symbol = req.symbol() == null ? null : req.symbol().trim().toUpperCase(Locale.ROOT);
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        long stockId = requireStockId(symbol);
        String type = req.ruleType() == null ? "" : req.ruleType().trim().toUpperCase(Locale.ROOT);
        if (!List.of("PRICE_BREAKOUT", "MA_CROSS", "VOLUME_SURGE").contains(type)) {
            throw new IllegalArgumentException("Invalid ruleType: " + type);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("profileId", profileId)
                .addValue("securityId", stockId)
                .addValue("type", type)
                .addValue("enabled", req.enabled())
                .addValue("priceLevel", req.priceLevel())
                .addValue("priceDirection", req.priceDirection())
                .addValue("maPeriod", req.maPeriod())
                .addValue("maDirection", req.maDirection())
                .addValue("volumeMultiple", req.volumeMultiple());

        Long id = jdbc.queryForObject(
                """
                insert into market.alert_rule (
                    profile_id, security_id, rule_type, enabled,
                    price_level, price_direction,
                    ma_period, ma_direction,
                    volume_multiple
                )
                values (
                    :profileId, :securityId, :type, :enabled,
                    :priceLevel, :priceDirection,
                    :maPeriod, :maDirection,
                    :volumeMultiple
                )
                returning id
                """,
                params,
                Long.class
        );
        if (id == null) throw new IllegalStateException("Failed to create rule");
        return listAlertRules(profileKey).stream().filter(r -> r.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to load created rule"));
    }

    public AlertRuleDto updateAlertRule(String profileKey, long id, UpdateAlertRuleRequestDto req) {
        if (req == null) throw new IllegalArgumentException("Request is required");
        long profileId = requireProfileId(profileKey);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("profileId", profileId)
                .addValue("enabled", req.enabled())
                .addValue("priceLevel", req.priceLevel())
                .addValue("priceDirection", req.priceDirection())
                .addValue("maPeriod", req.maPeriod())
                .addValue("maDirection", req.maDirection())
                .addValue("volumeMultiple", req.volumeMultiple());

        int updated = jdbc.update(
                """
                update market.alert_rule
                set
                    enabled = :enabled,
                    price_level = coalesce(:priceLevel, price_level),
                    price_direction = coalesce(:priceDirection, price_direction),
                    ma_period = coalesce(:maPeriod, ma_period),
                    ma_direction = coalesce(:maDirection, ma_direction),
                    volume_multiple = coalesce(:volumeMultiple, volume_multiple),
                    updated_at = now()
                where id = :id and profile_id = :profileId
                """,
                params
        );
        if (updated == 0) throw new IllegalArgumentException("Alert rule not found: " + id);
        return listAlertRules(profileKey).stream().filter(r -> r.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to load updated rule"));
    }

    public void deleteAlertRule(String profileKey, long id) {
        long profileId = requireProfileId(profileKey);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id).addValue("profileId", profileId);
        int deleted = jdbc.update("delete from market.alert_rule where id = :id and profile_id = :profileId", params);
        if (deleted == 0) throw new IllegalArgumentException("Alert rule not found: " + id);
    }

    public List<AlertEventDto> listAlertEvents(String profileKey, int limit) {
        long profileId = requireProfileId(profileKey);
        int lim = Math.min(Math.max(limit, 1), 200);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("profileId", profileId).addValue("limit", lim);
        return jdbc.query(
                """
                select
                    e.id,
                    r.id as rule_id,
                    s.canonical_symbol as symbol,
                    s.name as name,
                    e.bar_date,
                    e.message,
                    e.created_at
                from market.alert_event e
                join market.alert_rule r on r.id = e.alert_rule_id
                join market.security s on s.id = r.security_id
                where r.profile_id = :profileId
                order by e.created_at desc
                limit :limit
                """,
                params,
                (rs, rowNum) -> new AlertEventDto(
                        rs.getLong("id"),
                        rs.getLong("rule_id"),
                        rs.getString("symbol"),
                        rs.getString("name"),
                        rs.getObject("bar_date", LocalDate.class),
                        rs.getString("message"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class)
                )
        );
    }

    private record LatestAlertMetrics(
            LocalDate barDate,
            BigDecimal close,
            BigDecimal prevClose,
            BigDecimal ma20,
            BigDecimal prevMa20,
            BigDecimal ma50,
            BigDecimal prevMa50,
            BigDecimal ma200,
            BigDecimal prevMa200,
            Long volume,
            BigDecimal vma50
    ) {}

    private LatestAlertMetrics getLatestAlertMetrics(long securityId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("securityId", securityId);
        return jdbc.query(
                """
                with base as (
                    select
                        bar_date,
                        close,
                        volume,
                        lag(close) over(order by bar_date) as prev_close,
                        avg(close) over(order by bar_date rows between 19 preceding and current row) as ma20,
                        avg(close) over(order by bar_date rows between 49 preceding and current row) as ma50,
                        avg(close) over(order by bar_date rows between 199 preceding and current row) as ma200,
                        avg(volume) over(order by bar_date rows between 49 preceding and current row) as vma50
                    from market.price_bar
                    where security_id = :securityId and interval = '1d'
                )
                select
                    bar_date,
                    close,
                    prev_close,
                    ma20,
                    lag(ma20) over(order by bar_date) as prev_ma20,
                    ma50,
                    lag(ma50) over(order by bar_date) as prev_ma50,
                    ma200,
                    lag(ma200) over(order by bar_date) as prev_ma200,
                    volume,
                    vma50
                from base
                order by bar_date desc
                limit 1
                """,
                params,
                rs -> {
                    if (!rs.next()) return null;
                    return new LatestAlertMetrics(
                            rs.getObject("bar_date", LocalDate.class),
                            rs.getBigDecimal("close"),
                            rs.getBigDecimal("prev_close"),
                            rs.getBigDecimal("ma20"),
                            rs.getBigDecimal("prev_ma20"),
                            rs.getBigDecimal("ma50"),
                            rs.getBigDecimal("prev_ma50"),
                            rs.getBigDecimal("ma200"),
                            rs.getBigDecimal("prev_ma200"),
                            rs.getObject("volume", Long.class),
                            rs.getBigDecimal("vma50")
                    );
                }
        );
    }

    public EvaluateAlertsResponseDto evaluateAlerts(String profileKey, int returnLatestLimit) {
        requireProfileId(profileKey);
        List<AlertRuleDto> rules = listAlertRules(profileKey).stream().filter(AlertRuleDto::enabled).toList();
        int triggered = 0;
        for (AlertRuleDto rule : rules) {
            long securityId = requireStockId(rule.symbol());
            LatestAlertMetrics m = getLatestAlertMetrics(securityId);
            if (m == null || m.barDate() == null) continue;
            if (rule.lastTriggeredDate() != null && rule.lastTriggeredDate().equals(m.barDate())) continue;

            boolean fire = false;
            String msg = null;
            if ("PRICE_BREAKOUT".equals(rule.ruleType())) {
                if (rule.priceLevel() != null && rule.priceDirection() != null && m.close() != null && m.prevClose() != null) {
                    BigDecimal level = BigDecimal.valueOf(rule.priceLevel());
                    if ("ABOVE".equalsIgnoreCase(rule.priceDirection())) {
                        fire = m.prevClose().compareTo(level) <= 0 && m.close().compareTo(level) > 0;
                        msg = "上破 " + level;
                    } else if ("BELOW".equalsIgnoreCase(rule.priceDirection())) {
                        fire = m.prevClose().compareTo(level) >= 0 && m.close().compareTo(level) < 0;
                        msg = "下破 " + level;
                    }
                }
            } else if ("MA_CROSS".equals(rule.ruleType())) {
                int period = rule.maPeriod() == null ? 50 : rule.maPeriod();
                BigDecimal ma = switch (period) {
                    case 20 -> m.ma20();
                    case 200 -> m.ma200();
                    default -> m.ma50();
                };
                BigDecimal prevMa = switch (period) {
                    case 20 -> m.prevMa20();
                    case 200 -> m.prevMa200();
                    default -> m.prevMa50();
                };
                if (ma != null && prevMa != null && m.close() != null && m.prevClose() != null && rule.maDirection() != null) {
                    if ("ABOVE".equalsIgnoreCase(rule.maDirection())) {
                        fire = m.prevClose().compareTo(prevMa) <= 0 && m.close().compareTo(ma) > 0;
                        msg = "上穿 MA" + period;
                    } else if ("BELOW".equalsIgnoreCase(rule.maDirection())) {
                        fire = m.prevClose().compareTo(prevMa) >= 0 && m.close().compareTo(ma) < 0;
                        msg = "下穿 MA" + period;
                    }
                }
            } else if ("VOLUME_SURGE".equals(rule.ruleType())) {
                if (rule.volumeMultiple() != null && m.volume() != null && m.vma50() != null) {
                    BigDecimal threshold = m.vma50().multiply(BigDecimal.valueOf(rule.volumeMultiple()));
                    fire = BigDecimal.valueOf(m.volume()).compareTo(threshold) >= 0;
                    msg = "放量 ≥ " + rule.volumeMultiple() + "x(50日均量)";
                }
            }

            if (!fire || msg == null) continue;

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("ruleId", rule.id())
                    .addValue("barDate", m.barDate())
                    .addValue("message", rule.symbol() + " " + msg);

            int inserted = jdbc.update(
                    """
                    insert into market.alert_event (alert_rule_id, bar_date, message)
                    values (:ruleId, :barDate, :message)
                    on conflict (alert_rule_id, bar_date) do nothing
                    """,
                    params
            );
            if (inserted > 0) {
                triggered += 1;
                jdbc.update(
                        """
                        update market.alert_rule
                        set last_triggered_date = :barDate, last_triggered_at = now(), updated_at = now()
                        where id = :ruleId
                        """,
                        new MapSqlParameterSource().addValue("ruleId", rule.id()).addValue("barDate", m.barDate())
                );
            }
        }

        List<AlertEventDto> latest = listAlertEvents(profileKey, returnLatestLimit);
        return new EvaluateAlertsResponseDto(triggered, latest);
    }

    public List<String> listIndexConstituentSymbols(String indexSymbol) {
        requireIndexId(indexSymbol);
        LocalDate asOf = getLatestIndexAsOfDate(indexSymbol).orElse(null);
        if (asOf == null) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("symbol", indexSymbol)
                .addValue("asOf", asOf);
        return jdbc.query(
                """
                select s.canonical_symbol as symbol
                from market.index_membership m
                join market.security idx on idx.id = m.index_id and idx.canonical_symbol = :symbol
                join market.security s on s.id = m.security_id
                where m.as_of_date = :asOf and s.security_type = 'STOCK'
                order by s.canonical_symbol asc
                """,
                params,
                (rs, rowNum) -> rs.getString("symbol")
        );
    }

    public void replaceIndexConstituents(String indexSymbol, List<String> stockSymbols, LocalDate asOf) {
        long indexId = requireIndexId(indexSymbol);
        List<String> symbols = stockSymbols == null ? List.of() : stockSymbols;
        Map<String, Long> idMap = resolveStockIds(symbols);
        if (idMap.size() != symbols.size()) {
            for (String s : symbols) {
                if (!idMap.containsKey(s)) {
                    throw new IllegalArgumentException("Stock not found: " + s);
                }
            }
        }

        jdbc.update(
                "delete from market.index_membership where index_id = :indexId and as_of_date = :asOf",
                new MapSqlParameterSource().addValue("indexId", indexId).addValue("asOf", asOf)
        );

        if (symbols.isEmpty()) {
            return;
        }

        MapSqlParameterSource existingParams = new MapSqlParameterSource()
                .addValue("indexId", indexId)
                .addValue("securityIds", idMap.values());
        List<Map.Entry<Long, LocalDate>> existingDates = jdbc.query(
                """
                select security_id, min(coalesce(date_first_added, as_of_date)) as first_added
                from market.index_membership
                where index_id = :indexId and security_id in (:securityIds)
                group by security_id
                """,
                existingParams,
                (rs, rowNum) -> Map.entry(rs.getLong("security_id"), rs.getObject("first_added", LocalDate.class))
        );
        Map<Long, LocalDate> firstAdded = new HashMap<>();
        for (Map.Entry<Long, LocalDate> e : existingDates) {
            firstAdded.put(e.getKey(), e.getValue());
        }

        for (String sym : symbols) {
            Long sid = idMap.get(sym);
            if (sid == null) continue;
            LocalDate first = firstAdded.getOrDefault(sid, asOf);
            jdbc.update(
                    """
                    insert into market.index_membership (index_id, security_id, as_of_date, date_first_added)
                    values (:indexId, :securityId, :asOf, :firstAdded)
                    on conflict (index_id, security_id, as_of_date) do nothing
                    """,
                    new MapSqlParameterSource()
                            .addValue("indexId", indexId)
                            .addValue("securityId", sid)
                            .addValue("asOf", asOf)
                            .addValue("firstAdded", first)
            );
        }
    }

    public BreadthSnapshotDto getBreadthSnapshot(String indexSymbol, double volumeSurgeMultiple) {
        requireIndexId(indexSymbol);
        LocalDate asOf = getLatestIndexAsOfDate(indexSymbol).orElse(null);
        if (asOf == null) {
            return new BreadthSnapshotDto(indexSymbol, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("indexSymbol", indexSymbol)
                .addValue("asOf", asOf)
                .addValue("volumeMultiple", volumeSurgeMultiple);

        record Counts(
                String asOfDate,
                int totalMembers,
                int membersWithData,
                int up,
                int down,
                int flat,
                int aboveMa20,
                int aboveMa50,
                int aboveMa200,
                int newHigh52w,
                int newLow52w,
                int volumeSurge
        ) {}

        Counts c = jdbc.query(
                """
                with idx as (
                    select id as index_id
                    from market.security
                    where security_type = 'INDEX' and canonical_symbol = :indexSymbol
                ),
                members as (
                    select m.security_id
                    from market.index_membership m
                    join idx on idx.index_id = m.index_id
                    where m.as_of_date = :asOf
                ),
                as_of_bar as (
                    select max(pb.bar_date) as bar_date
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    where pb.interval = '1d'
                ),
                bars as (
                    select
                        pb.security_id,
                        pb.bar_date,
                        pb.close,
                        pb.volume,
                        lag(pb.close) over(partition by pb.security_id order by pb.bar_date) as prev_close,
                        avg(pb.close) over(partition by pb.security_id order by pb.bar_date rows between 19 preceding and current row) as ma20,
                        avg(pb.close) over(partition by pb.security_id order by pb.bar_date rows between 49 preceding and current row) as ma50,
                        avg(pb.close) over(partition by pb.security_id order by pb.bar_date rows between 199 preceding and current row) as ma200,
                        max(pb.close) over(partition by pb.security_id order by pb.bar_date rows between 251 preceding and current row) as high252,
                        min(pb.close) over(partition by pb.security_id order by pb.bar_date rows between 251 preceding and current row) as low252,
                        avg(pb.volume) over(partition by pb.security_id order by pb.bar_date rows between 49 preceding and current row) as vma50
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    join as_of_bar a on pb.bar_date <= a.bar_date
                    where pb.interval = '1d'
                      and pb.bar_date >= (select bar_date from as_of_bar) - interval '400 days'
                ),
                latest as (
                    select distinct on (security_id)
                        security_id,
                        bar_date,
                        close,
                        volume,
                        prev_close,
                        ma20,
                        ma50,
                        ma200,
                        high252,
                        low252,
                        vma50,
                        (bar_date = (select bar_date from as_of_bar)) as has_today
                    from bars
                    order by security_id, bar_date desc
                )
                select
                    (select to_char(bar_date, 'YYYY-MM-DD') from as_of_bar) as as_of_date,
                    (select count(*) from members) as total_members,
                    count(*) filter (where has_today) as members_with_data,
                    count(*) filter (where has_today and prev_close is not null and close > prev_close) as up,
                    count(*) filter (where has_today and prev_close is not null and close < prev_close) as down,
                    count(*) filter (where has_today and prev_close is not null and close = prev_close) as flat,
                    count(*) filter (where has_today and ma20 is not null and close > ma20) as above_ma20,
                    count(*) filter (where has_today and ma50 is not null and close > ma50) as above_ma50,
                    count(*) filter (where has_today and ma200 is not null and close > ma200) as above_ma200,
                    count(*) filter (where has_today and high252 is not null and close >= high252) as new_high_52w,
                    count(*) filter (where has_today and low252 is not null and close <= low252) as new_low_52w,
                    count(*) filter (where has_today and vma50 is not null and volume is not null and volume >= vma50 * :volumeMultiple) as volume_surge
                from latest
                """,
                params,
                rs -> {
                    if (!rs.next()) {
                        return new Counts(null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
                    }
                    return new Counts(
                            rs.getString("as_of_date"),
                            rs.getInt("total_members"),
                            rs.getInt("members_with_data"),
                            rs.getInt("up"),
                            rs.getInt("down"),
                            rs.getInt("flat"),
                            rs.getInt("above_ma20"),
                            rs.getInt("above_ma50"),
                            rs.getInt("above_ma200"),
                            rs.getInt("new_high_52w"),
                            rs.getInt("new_low_52w"),
                            rs.getInt("volume_surge")
                    );
                }
        );

        return new BreadthSnapshotDto(
                indexSymbol,
                c.asOfDate(),
                c.totalMembers(),
                c.membersWithData(),
                c.up(),
                c.down(),
                c.flat(),
                c.aboveMa20(),
                c.aboveMa50(),
                c.aboveMa200(),
                c.newHigh52w(),
                c.newLow52w(),
                c.volumeSurge()
        );
    }

    public List<ScreenerItemDto> runScreener(String indexSymbol, String preset, int lookbackDays, int limit) {
        requireIndexId(indexSymbol);
        LocalDate asOf = getLatestIndexAsOfDate(indexSymbol).orElse(null);
        if (asOf == null) {
            return List.of();
        }
        boolean trend = preset == null || preset.isBlank() || preset.equalsIgnoreCase("trend");
        boolean breakout = preset != null && preset.equalsIgnoreCase("breakout");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("indexSymbol", indexSymbol)
                .addValue("asOf", asOf)
                .addValue("lookback", lookbackDays)
                .addValue("limit", limit);

        String whereExtra = "";
        String orderBy = "order by return_pct desc nulls last";
        if (breakout) {
            whereExtra = "and close >= high252 and high252 is not null";
            orderBy = "order by return_pct desc nulls last";
        } else if (trend) {
            whereExtra = "and ma50 is not null and close > ma50";
            orderBy = "order by return_pct desc nulls last";
        }

        return jdbc.query(
                """
                with idx as (
                    select id as index_id
                    from market.security
                    where security_type = 'INDEX' and canonical_symbol = :indexSymbol
                ),
                members as (
                    select m.security_id
                    from market.index_membership m
                    join idx on idx.index_id = m.index_id
                    where m.as_of_date = :asOf
                ),
                as_of_bar as (
                    select max(pb.bar_date) as bar_date
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    where pb.interval = '1d'
                ),
                bars as (
                    select
                        pb.security_id,
                        pb.bar_date,
                        pb.close,
                        pb.volume,
                        lag(pb.close, :lookback) over(partition by pb.security_id order by pb.bar_date) as close_lb,
                        avg(pb.close) over(partition by pb.security_id order by pb.bar_date rows between 49 preceding and current row) as ma50,
                        avg(pb.close) over(partition by pb.security_id order by pb.bar_date rows between 199 preceding and current row) as ma200,
                        max(pb.close) over(partition by pb.security_id order by pb.bar_date rows between 251 preceding and current row) as high252
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    join as_of_bar a on pb.bar_date <= a.bar_date
                    where pb.interval = '1d'
                      and pb.bar_date >= (select bar_date from as_of_bar) - interval '450 days'
                ),
                latest as (
                    select distinct on (security_id)
                        security_id,
                        bar_date,
                        close,
                        volume,
                        close_lb,
                        ma50,
                        ma200,
                        high252
                    from bars
                    order by security_id, bar_date desc
                )
                select
                    s.canonical_symbol as symbol,
                    s.name as name,
                    to_char(l.bar_date, 'YYYY-MM-DD') as as_of_date,
                    l.close as close,
                    case when l.close_lb is null or l.close_lb = 0 then null else ((l.close / l.close_lb) - 1.0) end as return_pct,
                    l.ma50 as ma50,
                    l.ma200 as ma200,
                    l.volume as volume
                from latest l
                join market.security s on s.id = l.security_id and s.security_type = 'STOCK'
                where l.bar_date = (select bar_date from as_of_bar)
                %s
                %s
                limit :limit
                """.formatted(whereExtra, orderBy),
                params,
                (rs, rowNum) -> new ScreenerItemDto(
                        rs.getString("symbol"),
                        rs.getString("name"),
                        rs.getString("as_of_date"),
                        rs.getObject("close") == null ? null : rs.getBigDecimal("close").doubleValue(),
                        rs.getObject("return_pct") == null ? null : rs.getBigDecimal("return_pct").doubleValue(),
                        rs.getObject("ma50") == null ? null : rs.getBigDecimal("ma50").doubleValue(),
                        rs.getObject("ma200") == null ? null : rs.getBigDecimal("ma200").doubleValue(),
                        rs.getObject("volume") == null ? null : rs.getLong("volume")
                )
        );
    }

    public List<StreakRankItemDto> rankLongestStreaks(
            String indexSymbol,
            String interval,
            int directionSign,
            LocalDate start,
            LocalDate end,
            int limit,
            double volumeMultiple,
            double flatThresholdPct
    ) {
        boolean listAll = indexSymbol == null || indexSymbol.isBlank() || "ALL".equalsIgnoreCase(indexSymbol);
        LocalDate asOf = null;
        if (!listAll) {
            requireIndexId(indexSymbol);
            asOf = getLatestIndexAsOfDate(indexSymbol).orElse(null);
            if (asOf == null) {
                return List.of();
            }
        }

        String itv = interval == null ? "1d" : interval.trim().toLowerCase();
        if (!itv.equals("1d") && !itv.equals("1w") && !itv.equals("1m")) {
            throw new IllegalArgumentException("Unsupported interval: " + interval);
        }
        int dir = directionSign >= 0 ? 1 : -1;

        double flatThreshold = Math.max(0.0, flatThresholdPct) / 100.0;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("indexSymbol", indexSymbol)
                .addValue("asOf", asOf)
                .addValue("start", start)
                .addValue("end", end)
                .addValue("dir", dir)
                .addValue("volumeMultiple", Math.max(0.0, volumeMultiple))
                .addValue("flatThreshold", flatThreshold)
                .addValue("limit", limit);

        String membersCte;
        if (listAll) {
            membersCte = """
                members as (
                    select id as security_id
                    from market.security
                    where security_type = 'STOCK'
                )
                """;
        } else {
            membersCte = """
                idx as (
                    select id as index_id
                    from market.security
                    where security_type = 'INDEX' and canonical_symbol = :indexSymbol
                ),
                members as (
                    select m.security_id
                    from market.index_membership m
                    join idx on idx.index_id = m.index_id
                    where m.as_of_date = :asOf
                )
                """;
        }

        String barsSql;
        if (itv.equals("1d")) {
            barsSql = "select security_id, bar_date, close, volume from raw";
        } else if (itv.equals("1w")) {
            barsSql = """
                select lc.security_id, lc.period as bar_date, lc.close, va.volume
                from (
                    select distinct on (security_id, period)
                        security_id,
                        date_trunc('week', bar_date)::date as period,
                        bar_date,
                        close
                    from raw
                    order by security_id, period, bar_date desc
                ) lc
                join (
                    select
                        security_id,
                        date_trunc('week', bar_date)::date as period,
                        sum(coalesce(volume, 0)) as volume
                    from raw
                    group by security_id, date_trunc('week', bar_date)::date
                ) va on va.security_id = lc.security_id and va.period = lc.period
                """;
        } else {
            barsSql = """
                select lc.security_id, lc.period as bar_date, lc.close, va.volume
                from (
                    select distinct on (security_id, period)
                        security_id,
                        date_trunc('month', bar_date)::date as period,
                        bar_date,
                        close
                    from raw
                    order by security_id, period, bar_date desc
                ) lc
                join (
                    select
                        security_id,
                        date_trunc('month', bar_date)::date as period,
                        sum(coalesce(volume, 0)) as volume
                    from raw
                    group by security_id, date_trunc('month', bar_date)::date
                ) va on va.security_id = lc.security_id and va.period = lc.period
                """;
        }

        String sql = """
                with
                %s,
                effective_end as (
                    select max(pb.bar_date) as bar_date
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    where pb.interval = '1d' and pb.bar_date <= :end
                ),
                raw as (
                    select pb.security_id, pb.bar_date, pb.close, pb.volume
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    join effective_end ee on pb.bar_date between :start and ee.bar_date
                    where pb.interval = '1d' and pb.close is not null
                ),
                bars as (
                    %s
                ),
                calc as (
                    select
                        security_id,
                        bar_date,
                        close,
                        volume,
                        lag(close) over(partition by security_id order by bar_date) as prev_close,
                        avg(volume) over(partition by security_id order by bar_date rows between 19 preceding and current row) as vol_ma20
                    from bars
                ),
                dirs as (
                    select
                        security_id,
                        bar_date,
                        case
                            when prev_close is null or close is null then null
                            when :volumeMultiple > 0 and (vol_ma20 is null or vol_ma20 = 0 or volume is null or volume < (vol_ma20 * :volumeMultiple)) then 0
                            when :flatThreshold > 0 and abs((close / prev_close) - 1.0) < :flatThreshold then 0
                            when close > prev_close then 1
                            when close < prev_close then -1
                            else 0
                        end as dir
                    from calc
                ),
                tagged as (
                    select
                        security_id,
                        bar_date,
                        dir,
                        sum(case when dir <> :dir then 1 else 0 end) over(partition by security_id order by bar_date) as grp
                    from dirs
                    where dir is not null
                ),
                segments as (
                    select
                        security_id,
                        grp,
                        min(bar_date) as start_date,
                        max(bar_date) as end_date,
                        count(*) as streak
                    from tagged
                    where dir = :dir
                    group by security_id, grp
                ),
                best as (
                    select distinct on (security_id)
                        security_id,
                        streak,
                        start_date,
                        end_date
                    from segments
                    order by security_id, streak desc, end_date desc
                )
                select
                    s.canonical_symbol as symbol,
                    s.name as name,
                    b.streak as streak,
                    to_char(b.start_date, 'YYYY-MM-DD') as start_date,
                    to_char(b.end_date, 'YYYY-MM-DD') as end_date
                from best b
                join market.security s on s.id = b.security_id and s.security_type = 'STOCK'
                order by b.streak desc, b.end_date desc
                limit :limit
                """.formatted(membersCte, barsSql);

        String dirLabel = dir > 0 ? "up" : "down";
        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new StreakRankItemDto(
                        rs.getString("symbol"),
                        rs.getString("name"),
                        itv,
                        dirLabel,
                        rs.getObject("streak") == null ? null : rs.getInt("streak"),
                        rs.getString("start_date"),
                        rs.getString("end_date")
                )
        );
    }

    public StreakRankItemDto getLongestStreakForSymbol(
            String stockSymbol,
            String interval,
            int directionSign,
            LocalDate start,
            LocalDate end,
            double volumeMultiple,
            double flatThresholdPct
    ) {
        long securityId = findSecurityIdBySymbol(stockSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Security not found: " + stockSymbol));

        String itv = interval == null ? "1d" : interval.trim().toLowerCase();
        if (!itv.equals("1d") && !itv.equals("1w") && !itv.equals("1m")) {
            throw new IllegalArgumentException("Unsupported interval: " + interval);
        }
        int dir = directionSign >= 0 ? 1 : -1;

        String name = jdbc.query(
                "select name from market.security where id = :id",
                new MapSqlParameterSource().addValue("id", securityId),
                rs -> rs.next() ? rs.getString("name") : stockSymbol
        );

        double flatThreshold = Math.max(0.0, flatThresholdPct) / 100.0;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("securityId", securityId)
                .addValue("start", start)
                .addValue("end", end)
                .addValue("dir", dir)
                .addValue("volumeMultiple", Math.max(0.0, volumeMultiple))
                .addValue("flatThreshold", flatThreshold);

        String barsSql;
        if (itv.equals("1d")) {
            barsSql = "select security_id, bar_date, close, volume from raw";
        } else if (itv.equals("1w")) {
            barsSql = """
                select lc.security_id, lc.period as bar_date, lc.close, va.volume
                from (
                    select distinct on (security_id, period)
                        security_id,
                        date_trunc('week', bar_date)::date as period,
                        bar_date,
                        close
                    from raw
                    order by security_id, period, bar_date desc
                ) lc
                join (
                    select
                        security_id,
                        date_trunc('week', bar_date)::date as period,
                        sum(coalesce(volume, 0)) as volume
                    from raw
                    group by security_id, date_trunc('week', bar_date)::date
                ) va on va.security_id = lc.security_id and va.period = lc.period
                """;
        } else {
            barsSql = """
                select lc.security_id, lc.period as bar_date, lc.close, va.volume
                from (
                    select distinct on (security_id, period)
                        security_id,
                        date_trunc('month', bar_date)::date as period,
                        bar_date,
                        close
                    from raw
                    order by security_id, period, bar_date desc
                ) lc
                join (
                    select
                        security_id,
                        date_trunc('month', bar_date)::date as period,
                        sum(coalesce(volume, 0)) as volume
                    from raw
                    group by security_id, date_trunc('month', bar_date)::date
                ) va on va.security_id = lc.security_id and va.period = lc.period
                """;
        }

        String sql = """
                with
                members as (
                    select :securityId::bigint as security_id
                ),
                effective_end as (
                    select max(pb.bar_date) as bar_date
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    where pb.interval = '1d' and pb.bar_date <= :end
                ),
                raw as (
                    select pb.security_id, pb.bar_date, pb.close, pb.volume
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    join effective_end ee on pb.bar_date between :start and ee.bar_date
                    where pb.interval = '1d' and pb.close is not null
                ),
                bars as (
                    %s
                ),
                calc as (
                    select
                        security_id,
                        bar_date,
                        close,
                        volume,
                        lag(close) over(partition by security_id order by bar_date) as prev_close,
                        avg(volume) over(partition by security_id order by bar_date rows between 19 preceding and current row) as vol_ma20
                    from bars
                ),
                dirs as (
                    select
                        security_id,
                        bar_date,
                        case
                            when prev_close is null or close is null then null
                            when :volumeMultiple > 0 and (vol_ma20 is null or vol_ma20 = 0 or volume is null or volume < (vol_ma20 * :volumeMultiple)) then 0
                            when :flatThreshold > 0 and abs((close / prev_close) - 1.0) < :flatThreshold then 0
                            when close > prev_close then 1
                            when close < prev_close then -1
                            else 0
                        end as dir
                    from calc
                ),
                tagged as (
                    select
                        security_id,
                        bar_date,
                        dir,
                        sum(case when dir <> :dir then 1 else 0 end) over(partition by security_id order by bar_date) as grp
                    from dirs
                    where dir is not null
                ),
                segments as (
                    select
                        security_id,
                        grp,
                        min(bar_date) as start_date,
                        max(bar_date) as end_date,
                        count(*) as streak
                    from tagged
                    where dir = :dir
                    group by security_id, grp
                ),
                best as (
                    select distinct on (security_id)
                        security_id,
                        streak,
                        start_date,
                        end_date
                    from segments
                    order by security_id, streak desc, end_date desc
                )
                select
                    streak,
                    to_char(start_date, 'YYYY-MM-DD') as start_date,
                    to_char(end_date, 'YYYY-MM-DD') as end_date
                from best
                """.formatted(barsSql);

        record Row(Integer streak, String startDate, String endDate) {}

        List<Row> rows = jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new Row(
                        rs.getObject("streak") == null ? null : rs.getInt("streak"),
                        rs.getString("start_date"),
                        rs.getString("end_date")
                )
        );

        String dirLabel = dir > 0 ? "up" : "down";
        if (rows.isEmpty()) {
            return new StreakRankItemDto(stockSymbol, name, itv, dirLabel, 0, null, null);
        }
        Row r = rows.get(0);
        return new StreakRankItemDto(stockSymbol, name, itv, dirLabel, r.streak(), r.startDate(), r.endDate());
    }

    public List<FactorRankItemDto> rankMaxDrawdown(
            String indexSymbol,
            String interval,
            LocalDate start,
            LocalDate end,
            int limit,
            boolean best
    ) {
        boolean listAll = indexSymbol == null || indexSymbol.isBlank() || "ALL".equalsIgnoreCase(indexSymbol);
        LocalDate asOf = null;
        if (!listAll) {
            requireIndexId(indexSymbol);
            asOf = getLatestIndexAsOfDate(indexSymbol).orElse(null);
            if (asOf == null) {
                return List.of();
            }
        }

        String itv = interval == null ? "1d" : interval.trim().toLowerCase();
        if (!itv.equals("1d") && !itv.equals("1w") && !itv.equals("1m")) {
            throw new IllegalArgumentException("Unsupported interval: " + interval);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("indexSymbol", indexSymbol)
                .addValue("asOf", asOf)
                .addValue("start", start)
                .addValue("end", end)
                .addValue("limit", limit);

        String membersCte;
        if (listAll) {
            membersCte = """
                members as (
                    select id as security_id
                    from market.security
                    where security_type = 'STOCK'
                )
                """;
        } else {
            membersCte = """
                idx as (
                    select id as index_id
                    from market.security
                    where security_type = 'INDEX' and canonical_symbol = :indexSymbol
                ),
                members as (
                    select m.security_id
                    from market.index_membership m
                    join idx on idx.index_id = m.index_id
                    where m.as_of_date = :asOf
                )
                """;
        }

        String barsSql = buildCloseBarsSql(itv);
        String order = best ? "mdd desc nulls last" : "mdd asc nulls last";
        String sql = """
                with
                %s,
                effective_end as (
                    select max(pb.bar_date) as bar_date
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    where pb.interval = '1d' and pb.bar_date <= :end
                ),
                raw as (
                    select pb.security_id, pb.bar_date, pb.close
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    join effective_end ee on pb.bar_date between :start and ee.bar_date
                    where pb.interval = '1d' and pb.close is not null
                ),
                bars as (
                    %s
                ),
                calc as (
                    select
                        security_id,
                        bar_date,
                        close,
                        max(close) over(partition by security_id order by bar_date rows between unbounded preceding and current row) as run_max
                    from bars
                ),
                dd as (
                    select
                        security_id,
                        bar_date,
                        case when run_max is null or run_max = 0 then null else (close / run_max - 1.0) end as dd
                    from calc
                ),
                mdd as (
                    select security_id, min(dd) as mdd
                    from dd
                    group by security_id
                ),
                trough as (
                    select distinct on (d.security_id)
                        d.security_id,
                        d.bar_date as trough_date
                    from dd d
                    join mdd m on m.security_id = d.security_id and m.mdd = d.dd
                    order by d.security_id, d.bar_date desc
                )
                select
                    s.canonical_symbol as symbol,
                    s.name as name,
                    m.mdd as mdd,
                    to_char(t.trough_date, 'YYYY-MM-DD') as end_date
                from mdd m
                join market.security s on s.id = m.security_id and s.security_type = 'STOCK'
                left join trough t on t.security_id = m.security_id
                order by %s
                limit :limit
                """.formatted(membersCte, barsSql, order);

        String metric = "max_drawdown";
        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new FactorRankItemDto(
                        rs.getString("symbol"),
                        rs.getString("name"),
                        metric,
                        rs.getObject("mdd") == null ? null : rs.getBigDecimal("mdd").doubleValue(),
                        null,
                        null,
                        null,
                        rs.getString("end_date")
                )
        );
    }

    public List<FactorRankItemDto> rankMaxRundown(
            String indexSymbol,
            String interval,
            LocalDate start,
            LocalDate end,
            int limit
    ) {
        List<FactorRankItemDto> rows = rankMaxDrawdown(indexSymbol, interval, start, end, limit, false);
        return rows.stream()
                .map(r -> new FactorRankItemDto(r.symbol(), r.name(), "max_rundown", r.value(), r.count(), r.rate(), r.startDate(), r.endDate()))
                .toList();
    }

    public List<FactorRankItemDto> rankMaxRunup(
            String indexSymbol,
            String interval,
            LocalDate start,
            LocalDate end,
            int limit
    ) {
        boolean listAll = indexSymbol == null || indexSymbol.isBlank() || "ALL".equalsIgnoreCase(indexSymbol);
        LocalDate asOf = null;
        if (!listAll) {
            requireIndexId(indexSymbol);
            asOf = getLatestIndexAsOfDate(indexSymbol).orElse(null);
            if (asOf == null) {
                return List.of();
            }
        }

        String itv = interval == null ? "1d" : interval.trim().toLowerCase();
        if (!itv.equals("1d") && !itv.equals("1w") && !itv.equals("1m")) {
            throw new IllegalArgumentException("Unsupported interval: " + interval);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("indexSymbol", indexSymbol)
                .addValue("asOf", asOf)
                .addValue("start", start)
                .addValue("end", end)
                .addValue("limit", limit);

        String membersCte;
        if (listAll) {
            membersCte = """
                members as (
                    select id as security_id
                    from market.security
                    where security_type = 'STOCK'
                )
                """;
        } else {
            membersCte = """
                idx as (
                    select id as index_id
                    from market.security
                    where security_type = 'INDEX' and canonical_symbol = :indexSymbol
                ),
                members as (
                    select m.security_id
                    from market.index_membership m
                    join idx on idx.index_id = m.index_id
                    where m.as_of_date = :asOf
                )
                """;
        }

        String barsSql = buildCloseBarsSql(itv);
        String sql = """
                with
                %s,
                effective_end as (
                    select max(pb.bar_date) as bar_date
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    where pb.interval = '1d' and pb.bar_date <= :end
                ),
                raw as (
                    select pb.security_id, pb.bar_date, pb.close
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    join effective_end ee on pb.bar_date between :start and ee.bar_date
                    where pb.interval = '1d' and pb.close is not null
                ),
                bars as (
                    %s
                ),
                calc as (
                    select
                        security_id,
                        bar_date,
                        close,
                        min(close) over(partition by security_id order by bar_date rows between unbounded preceding and current row) as run_min
                    from bars
                ),
                ru as (
                    select
                        security_id,
                        bar_date,
                        case when run_min is null or run_min = 0 then null else (close / run_min - 1.0) end as ru
                    from calc
                ),
                mru as (
                    select security_id, max(ru) as mru
                    from ru
                    group by security_id
                ),
                peak as (
                    select distinct on (r.security_id)
                        r.security_id,
                        r.bar_date as peak_date
                    from ru r
                    join mru m on m.security_id = r.security_id and m.mru = r.ru
                    order by r.security_id, r.bar_date desc
                )
                select
                    s.canonical_symbol as symbol,
                    s.name as name,
                    m.mru as mru,
                    to_char(p.peak_date, 'YYYY-MM-DD') as end_date
                from mru m
                join market.security s on s.id = m.security_id and s.security_type = 'STOCK'
                left join peak p on p.security_id = m.security_id
                order by m.mru desc nulls last
                limit :limit
                """.formatted(membersCte, barsSql);

        String metric = "max_runup";
        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new FactorRankItemDto(
                        rs.getString("symbol"),
                        rs.getString("name"),
                        metric,
                        rs.getObject("mru") == null ? null : rs.getBigDecimal("mru").doubleValue(),
                        null,
                        null,
                        null,
                        rs.getString("end_date")
                )
        );
    }

    public List<FactorRankItemDto> rankNewHighLowCounts(
            String indexSymbol,
            String interval,
            LocalDate start,
            LocalDate end,
            int limit,
            int lookback,
            boolean high
    ) {
        boolean listAll = indexSymbol == null || indexSymbol.isBlank() || "ALL".equalsIgnoreCase(indexSymbol);
        LocalDate asOf = null;
        if (!listAll) {
            requireIndexId(indexSymbol);
            asOf = getLatestIndexAsOfDate(indexSymbol).orElse(null);
            if (asOf == null) {
                return List.of();
            }
        }

        String itv = interval == null ? "1d" : interval.trim().toLowerCase();
        if (!itv.equals("1d") && !itv.equals("1w") && !itv.equals("1m")) {
            throw new IllegalArgumentException("Unsupported interval: " + interval);
        }

        int lb = Math.min(Math.max(lookback, 2), 2000);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("indexSymbol", indexSymbol)
                .addValue("asOf", asOf)
                .addValue("start", start)
                .addValue("end", end)
                .addValue("limit", limit);

        String membersCte;
        if (listAll) {
            membersCte = """
                members as (
                    select id as security_id
                    from market.security
                    where security_type = 'STOCK'
                )
                """;
        } else {
            membersCte = """
                idx as (
                    select id as index_id
                    from market.security
                    where security_type = 'INDEX' and canonical_symbol = :indexSymbol
                ),
                members as (
                    select m.security_id
                    from market.index_membership m
                    join idx on idx.index_id = m.index_id
                    where m.as_of_date = :asOf
                )
                """;
        }

        String barsSql = buildCloseBarsSql(itv);
        String statField = high ? "highs" : "lows";
        String metric = high ? "new_high_count" : "new_low_count";
        String sql = """
                with
                %s,
                effective_end as (
                    select max(pb.bar_date) as bar_date
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    where pb.interval = '1d' and pb.bar_date <= :end
                ),
                raw as (
                    select pb.security_id, pb.bar_date, pb.close
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    join effective_end ee on pb.bar_date between :start and ee.bar_date
                    where pb.interval = '1d' and pb.close is not null
                ),
                bars as (
                    %s
                ),
                calc as (
                    select
                        security_id,
                        bar_date,
                        close,
                        max(close) over(partition by security_id order by bar_date rows between %d preceding and 1 preceding) as prev_max,
                        min(close) over(partition by security_id order by bar_date rows between %d preceding and 1 preceding) as prev_min
                    from bars
                ),
                stats as (
                    select
                        security_id,
                        sum(case when prev_max is not null and close > prev_max then 1 else 0 end) as highs,
                        sum(case when prev_min is not null and close < prev_min then 1 else 0 end) as lows,
                        sum(case when prev_max is not null then 1 else 0 end) as evals
                    from calc
                    group by security_id
                )
                select
                    s.canonical_symbol as symbol,
                    s.name as name,
                    st.%s as cnt,
                    st.evals as evals
                from stats st
                join market.security s on s.id = st.security_id and s.security_type = 'STOCK'
                order by st.%s desc nulls last
                limit :limit
                """.formatted(membersCte, barsSql, lb - 1, lb - 1, statField, statField);

        return jdbc.query(
                sql,
                params,
                (rs, rowNum) -> {
                    Integer cnt = rs.getObject("cnt") == null ? null : rs.getInt("cnt");
                    Integer evals = rs.getObject("evals") == null ? null : rs.getInt("evals");
                    Double rate = (cnt != null && evals != null && evals > 0) ? (cnt.doubleValue() / evals.doubleValue()) : null;
                    return new FactorRankItemDto(
                            rs.getString("symbol"),
                            rs.getString("name"),
                            metric,
                            null,
                            cnt,
                            rate,
                            null,
                            null
                    );
                }
        );
    }

    private static String buildCloseBarsSql(String itv) {
        if ("1w".equals(itv)) {
            return """
                select distinct on (security_id, period)
                    security_id,
                    period as bar_date,
                    close
                from (
                    select
                        security_id,
                        date_trunc('week', bar_date)::date as period,
                        bar_date,
                        close
                    from raw
                ) t
                order by security_id, period, bar_date desc
                """;
        }
        if ("1m".equals(itv)) {
            return """
                select distinct on (security_id, period)
                    security_id,
                    period as bar_date,
                    close
                from (
                    select
                        security_id,
                        date_trunc('month', bar_date)::date as period,
                        bar_date,
                        close
                    from raw
                ) t
                order by security_id, period, bar_date desc
                """;
        }
        return "select security_id, bar_date, close from raw";
    }

    public RsSeriesDto getRelativeStrengthSeries(String stockSymbol, String indexSymbol, LocalDate start, LocalDate end) {
        if (stockSymbol == null || stockSymbol.isBlank()) {
            throw new IllegalArgumentException("symbol is required");
        }
        requireIndexId(indexSymbol);
        long stockId = findSecurityIdBySymbol(stockSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Security not found: " + stockSymbol));
        long indexId = requireIndexId(indexSymbol);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("stockId", stockId)
                .addValue("indexId", indexId)
                .addValue("start", start)
                .addValue("end", end);

        record Row(LocalDate date, Double stockClose, Double indexClose) {}

        List<Row> rows = jdbc.query(
                """
                with s as (
                    select bar_date, close
                    from market.price_bar
                    where security_id = :stockId and interval = '1d' and bar_date between :start and :end
                ),
                i as (
                    select bar_date, close
                    from market.price_bar
                    where security_id = :indexId and interval = '1d' and bar_date between :start and :end
                )
                select s.bar_date as bar_date, s.close as stock_close, i.close as index_close
                from s
                join i on i.bar_date = s.bar_date
                where s.close is not null and i.close is not null and i.close <> 0
                order by s.bar_date
                """,
                params,
                (rs, rowNum) -> new Row(
                        rs.getObject("bar_date", LocalDate.class),
                        rs.getObject("stock_close") == null ? null : rs.getBigDecimal("stock_close").doubleValue(),
                        rs.getObject("index_close") == null ? null : rs.getBigDecimal("index_close").doubleValue()
                )
        );

        if (rows.isEmpty()) {
            return new RsSeriesDto(stockSymbol, indexSymbol, start.toString(), end.toString(), null, null, null, List.of());
        }

        double firstStock = rows.get(0).stockClose() == null ? 0 : rows.get(0).stockClose();
        double firstIndex = rows.get(0).indexClose() == null ? 0 : rows.get(0).indexClose();
        Double firstRs = firstIndex == 0 ? null : (firstStock / firstIndex);

        Double lastStock = rows.get(rows.size() - 1).stockClose();
        Double lastIndex = rows.get(rows.size() - 1).indexClose();
        Double stockReturn = (firstStock != 0 && lastStock != null) ? (lastStock / firstStock - 1.0) : null;
        Double indexReturn = (firstIndex != 0 && lastIndex != null) ? (lastIndex / firstIndex - 1.0) : null;
        Double rsReturn = (stockReturn != null && indexReturn != null)
                ? ((1.0 + stockReturn) / (1.0 + indexReturn) - 1.0)
                : null;

        List<RsPointDto> points = rows.stream().map(r -> {
            Double rs = (r.indexClose() == null || r.indexClose() == 0 || r.stockClose() == null) ? null : (r.stockClose() / r.indexClose());
            Double rsNorm = (rs != null && firstRs != null && firstRs != 0) ? (rs / firstRs) : null;
            return new RsPointDto(
                    r.date().toString(),
                    r.stockClose(),
                    r.indexClose(),
                    rs,
                    rsNorm
            );
        }).toList();

        return new RsSeriesDto(stockSymbol, indexSymbol, start.toString(), end.toString(), stockReturn, indexReturn, rsReturn, points);
    }

    public List<RsRankItemDto> rankRelativeStrength(String indexSymbol, int lookbackDays, int limit, boolean requireAboveMa50) {
        long indexId = requireIndexId(indexSymbol);
        LocalDate asOf = getLatestIndexAsOfDate(indexSymbol).orElse(null);
        if (asOf == null) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("indexSymbol", indexSymbol)
                .addValue("indexId", indexId)
                .addValue("asOf", asOf)
                .addValue("lookback", lookbackDays)
                .addValue("limit", limit)
                .addValue("requireMa50", requireAboveMa50);

        return jdbc.query(
                """
                with members as (
                    select m.security_id
                    from market.index_membership m
                    join market.security idx on idx.id = m.index_id and idx.canonical_symbol = :indexSymbol and idx.security_type = 'INDEX'
                    where m.as_of_date = :asOf
                ),
                idx_bars as (
                    select
                        pb.bar_date,
                        pb.close,
                        lag(pb.close, :lookback) over(order by pb.bar_date) as close_lb
                    from market.price_bar pb
                    where pb.security_id = :indexId and pb.interval = '1d'
                ),
                idx_latest as (
                    select max(bar_date) as bar_date
                    from idx_bars
                ),
                idx_at as (
                    select
                        to_char(b.bar_date, 'YYYY-MM-DD') as as_of_date,
                        b.close as idx_close,
                        b.close_lb as idx_close_lb
                    from idx_bars b
                    join idx_latest l on l.bar_date = b.bar_date
                    where b.close is not null and b.close_lb is not null and b.close_lb <> 0
                ),
                stock_bars as (
                    select
                        pb.security_id,
                        pb.bar_date,
                        pb.close,
                        lag(pb.close, :lookback) over(partition by pb.security_id order by pb.bar_date) as close_lb,
                        avg(pb.close) over(partition by pb.security_id order by pb.bar_date rows between 49 preceding and current row) as ma50
                    from market.price_bar pb
                    join members mem on mem.security_id = pb.security_id
                    where pb.interval = '1d'
                      and pb.bar_date >= (select bar_date from idx_latest) - interval '500 days'
                ),
                stock_at as (
                    select distinct on (security_id)
                        security_id,
                        bar_date,
                        close,
                        close_lb,
                        ma50
                    from stock_bars
                    where bar_date = (select bar_date from idx_latest)
                    order by security_id, bar_date desc
                )
                select
                    s.canonical_symbol as symbol,
                    s.name as name,
                    (select as_of_date from idx_at) as as_of_date,
                    case when sa.close_lb is null or sa.close_lb = 0 then null else (sa.close / sa.close_lb - 1.0) end as stock_return_pct,
                    case when ia.idx_close_lb is null or ia.idx_close_lb = 0 then null else (ia.idx_close / ia.idx_close_lb - 1.0) end as index_return_pct,
                    case
                        when sa.close_lb is null or sa.close_lb = 0 or ia.idx_close_lb is null or ia.idx_close_lb = 0
                        then null
                        else ((sa.close / sa.close_lb) / (ia.idx_close / ia.idx_close_lb) - 1.0)
                    end as rs_return_pct
                from stock_at sa
                join market.security s on s.id = sa.security_id and s.security_type = 'STOCK'
                cross join idx_at ia
                where sa.close is not null and sa.close_lb is not null and sa.close_lb <> 0
                  and (:requireMa50 = false or (sa.ma50 is not null and sa.close > sa.ma50))
                order by rs_return_pct desc nulls last
                limit :limit
                """,
                params,
                (rs, rowNum) -> new RsRankItemDto(
                        rs.getString("symbol"),
                        rs.getString("name"),
                        rs.getString("as_of_date"),
                        rs.getObject("stock_return_pct") == null ? null : rs.getBigDecimal("stock_return_pct").doubleValue(),
                        rs.getObject("index_return_pct") == null ? null : rs.getBigDecimal("index_return_pct").doubleValue(),
                        rs.getObject("rs_return_pct") == null ? null : rs.getBigDecimal("rs_return_pct").doubleValue()
                )
        );
    }

    public StockDetailDto getStockDetail(String canonicalSymbol, String lang) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("symbol", canonicalSymbol)
                .addValue("lang", lang);

        StockDetailDto detail = jdbc.query(
                """
                select
                    s.id as security_id,
                    s.canonical_symbol as symbol,
                    s.name as name,
                    sd.sector,
                    sd.sub_industry,
                    sd.headquarters,
                    m.date_first_added,
                    sd.cik,
                    sd.founded,
                    sd.wiki_url,
                    ws.title as wiki_title,
                    ws.description as wiki_description,
                    ws.extract as wiki_extract,
                    fs.shares_outstanding,
                    fs.float_shares,
                    fs.market_cap,
                    fs.currency
                from market.security s
                left join market.security_detail sd on sd.security_id = s.id
                left join market.index_membership m on m.security_id = s.id
                left join market.wiki_summary ws on ws.security_id = s.id and ws.lang = :lang
                left join lateral (
                    select shares_outstanding, float_shares, market_cap, currency
                    from market.fundamental_snapshot
                    where security_id = s.id
                    order by as_of_date desc, created_at desc
                    limit 1
                ) fs on true
                where s.canonical_symbol = :symbol and s.security_type = 'STOCK'
                order by m.as_of_date desc nulls last
                limit 1
                """,
                params,
                rs -> {
                    if (!rs.next()) return null;
                    long securityId = rs.getLong("security_id");
                    List<SecurityIdentifierDto> identifiers = listIdentifiers(securityId);
                    List<CorporateActionDto> corporateActions = listCorporateActions(securityId);
                    return new StockDetailDto(
                            rs.getString("symbol"),
                            rs.getString("name"),
                            rs.getString("sector"),
                            rs.getString("sub_industry"),
                            rs.getString("headquarters"),
                            rs.getObject("date_first_added", LocalDate.class),
                            rs.getString("cik"),
                            rs.getString("founded"),
                            rs.getString("wiki_url"),
                            rs.getString("wiki_title"),
                            rs.getString("wiki_description"),
                            rs.getString("wiki_extract"),
                            rs.getObject("shares_outstanding", Long.class),
                            rs.getObject("float_shares", Long.class),
                            rs.getBigDecimal("market_cap"),
                            rs.getString("currency"),
                            identifiers,
                            corporateActions
                    );
                }
        );

        if (detail == null) {
            throw new IllegalArgumentException("Stock not found: " + canonicalSymbol);
        }
        return detail;
    }

    public List<SecurityIdentifierDto> listIdentifiers(long securityId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("securityId", securityId);
        return jdbc.query(
                """
                select provider, identifier
                from market.security_identifier
                where security_id = :securityId
                """,
                params,
                (rs, rowNum) -> new SecurityIdentifierDto(rs.getString("provider"), rs.getString("identifier"))
        );
    }

    public List<CorporateActionDto> listCorporateActions(long securityId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("securityId", securityId);
        return jdbc.query(
                """
                select ex_date, action_type, cash_amount, currency, split_numerator, split_denominator, source
                from market.corporate_action
                where security_id = :securityId
                order by ex_date desc
                """,
                params,
                (rs, rowNum) -> new CorporateActionDto(
                        rs.getObject("ex_date", LocalDate.class),
                        rs.getString("action_type"),
                        rs.getBigDecimal("cash_amount"),
                        rs.getString("currency"),
                        rs.getObject("split_numerator", Integer.class),
                        rs.getObject("split_denominator", Integer.class),
                        rs.getString("source")
                )
        );
    }

    public List<CorporateActionDto> listCorporateActionsBySymbol(String canonicalSymbol, LocalDate start, LocalDate end) {
        long securityId = findSecurityIdBySymbol(canonicalSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Security not found: " + canonicalSymbol));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("securityId", securityId)
                .addValue("start", start)
                .addValue("end", end);
        return jdbc.query(
                """
                select ex_date, action_type, cash_amount, currency, split_numerator, split_denominator, source
                from market.corporate_action
                where security_id = :securityId
                  and ex_date between :start and :end
                order by ex_date desc
                """,
                params,
                (rs, rowNum) -> new CorporateActionDto(
                        rs.getObject("ex_date", LocalDate.class),
                        rs.getString("action_type"),
                        rs.getBigDecimal("cash_amount"),
                        rs.getString("currency"),
                        rs.getObject("split_numerator", Integer.class),
                        rs.getObject("split_denominator", Integer.class),
                        rs.getString("source")
                )
        );
    }

    public List<BarDto> getBarsBySymbol(
            String canonicalSymbol,
            String interval,
            LocalDate start,
            LocalDate end
    ) {
        long securityId = findSecurityIdBySymbol(canonicalSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Security not found: " + canonicalSymbol));

        String iv = interval == null ? "1d" : interval.toLowerCase(Locale.ROOT);
        if (!iv.equals("1d")) {
            List<BarDto> daily = getBarsBySecurityId(securityId, "1d", start, end);
            return aggregateIfNeeded(daily, iv);
        }

        return getBarsBySecurityId(securityId, "1d", start, end);
    }

    private List<BarDto> getBarsBySecurityId(
            long securityId,
            String interval,
            LocalDate start,
            LocalDate end
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("securityId", securityId)
                .addValue("interval", interval)
                .addValue("start", start)
                .addValue("end", end);

        return jdbc.query(
                """
                select bar_date, open, high, low, close, volume
                from market.price_bar
                where security_id = :securityId
                  and interval = :interval
                  and bar_date between :start and :end
                order by bar_date
                """,
                params,
                BAR_MAPPER
        );
    }

    private static List<BarDto> aggregateIfNeeded(List<BarDto> daily, String interval) {
        if (interval.equals("1w")) return aggregateWeekly(daily);
        if (interval.equals("1m")) return aggregateMonthly(daily);
        if (interval.equals("1q")) return aggregateQuarterly(daily);
        if (interval.equals("1y")) return aggregateYearly(daily);
        throw new IllegalArgumentException("interval must be one of: 1d, 1w, 1m, 1q, 1y");
    }

    private static List<BarDto> aggregateWeekly(List<BarDto> daily) {
        WeekFields wf = WeekFields.ISO;
        Map<String, List<BarDto>> groups = new LinkedHashMap<>();
        for (BarDto b : daily) {
            LocalDate d = b.date();
            int wy = d.get(wf.weekBasedYear());
            int ww = d.get(wf.weekOfWeekBasedYear());
            String key = wy + "-" + ww;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        return aggregateGroups(groups.values());
    }

    private static List<BarDto> aggregateMonthly(List<BarDto> daily) {
        Map<YearMonth, List<BarDto>> groups = new LinkedHashMap<>();
        for (BarDto b : daily) {
            YearMonth ym = YearMonth.from(b.date());
            groups.computeIfAbsent(ym, k -> new ArrayList<>()).add(b);
        }
        return aggregateGroups(groups.values());
    }

    private static List<BarDto> aggregateQuarterly(List<BarDto> daily) {
        Map<String, List<BarDto>> groups = new LinkedHashMap<>();
        for (BarDto b : daily) {
            LocalDate d = b.date();
            int q = (d.getMonthValue() - 1) / 3 + 1;
            String key = d.getYear() + "-Q" + q;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
        }
        return aggregateGroups(groups.values());
    }

    private static List<BarDto> aggregateYearly(List<BarDto> daily) {
        Map<Integer, List<BarDto>> groups = new LinkedHashMap<>();
        for (BarDto b : daily) {
            groups.computeIfAbsent(b.date().getYear(), k -> new ArrayList<>()).add(b);
        }
        return aggregateGroups(groups.values());
    }

    private static List<BarDto> aggregateGroups(Collection<List<BarDto>> groups) {
        List<BarDto> out = new ArrayList<>();
        for (List<BarDto> g : groups) {
            if (g.isEmpty()) continue;
            g.sort(Comparator.comparing(BarDto::date));
            BarDto first = g.get(0);
            BarDto last = g.get(g.size() - 1);
            BigDecimal open = first.open();
            BigDecimal close = last.close();
            BigDecimal high = null;
            BigDecimal low = null;
            long vol = 0;
            boolean hasVol = false;
            for (BarDto b : g) {
                if (b.high() != null) {
                    high = high == null ? b.high() : high.max(b.high());
                }
                if (b.low() != null) {
                    low = low == null ? b.low() : low.min(b.low());
                }
                if (b.volume() != null) {
                    vol += b.volume();
                    hasVol = true;
                }
            }
            out.add(new BarDto(last.date(), open, high, low, close, hasVol ? vol : null));
        }
        out.sort(Comparator.comparing(BarDto::date));
        return out;
    }

    private static final RowMapper<BarDto> BAR_MAPPER = (rs, rowNum) -> new BarDto(
            rs.getObject("bar_date", LocalDate.class),
            rs.getObject("open", BigDecimal.class),
            rs.getObject("high", BigDecimal.class),
            rs.getObject("low", BigDecimal.class),
            rs.getObject("close", BigDecimal.class),
            rs.getObject("volume") == null ? null : rs.getLong("volume")
    );
}
