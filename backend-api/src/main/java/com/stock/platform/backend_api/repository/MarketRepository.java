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
            asOf = getLatestIndexAsOfDate(indexSymbol)
                    .orElseThrow(() -> new IllegalStateException("Index list not loaded: " + indexSymbol));
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
            asOf = getLatestIndexAsOfDate(indexSymbol)
                    .orElseThrow(() -> new IllegalStateException("Index list not loaded: " + indexSymbol));
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
