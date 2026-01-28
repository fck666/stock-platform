package com.stock.platform.backend_api.repository;

import com.stock.platform.backend_api.api.dto.BarDto;
import com.stock.platform.backend_api.api.dto.PagedResponse;
import com.stock.platform.backend_api.api.dto.SecurityIdentifierDto;
import com.stock.platform.backend_api.api.dto.StockDetailDto;
import com.stock.platform.backend_api.api.dto.StockListItemDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    public PagedResponse<StockListItemDto> listIndexStocks(
            String indexSymbol,
            String query,
            int page,
            int size,
            String lang
    ) {
        LocalDate asOf = getLatestIndexAsOfDate(indexSymbol)
                .orElseThrow(() -> new IllegalStateException("Index list not loaded: " + indexSymbol));

        String q = query == null ? null : query.trim();
        boolean hasQuery = q != null && !q.isBlank();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("indexSymbol", indexSymbol)
                .addValue("asOf", asOf)
                .addValue("lang", lang)
                .addValue("limit", size)
                .addValue("offset", Math.max(0, page) * size);

        if (hasQuery) {
            params.addValue("qLike", "%" + q + "%");
        }

        String where = hasQuery
                ? "and (s.canonical_symbol ilike :qLike or s.name ilike :qLike or ws.title ilike :qLike or ws.description ilike :qLike)"
                : "";

        long total = jdbc.queryForObject(
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

        List<StockListItemDto> items = jdbc.query(
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
                order by s.canonical_symbol
                limit :limit offset :offset
                """.formatted(where),
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

        return new PagedResponse<>(items, total, page, size);
    }

    public PagedResponse<StockListItemDto> listSp500Stocks(
            String query,
            int page,
            int size,
            String lang
    ) {
        return listIndexStocks("^SPX", query, page, size, lang);
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
                    sd.shares_outstanding,
                    sd.market_cap
                from market.security s
                left join market.security_detail sd on sd.security_id = s.id
                left join market.index_membership m on m.security_id = s.id
                left join market.wiki_summary ws on ws.security_id = s.id and ws.lang = :lang
                where s.canonical_symbol = :symbol and s.security_type = 'STOCK'
                order by m.as_of_date desc nulls last
                limit 1
                """,
                params,
                rs -> {
                    if (!rs.next()) return null;
                    long securityId = rs.getLong("security_id");
                    List<SecurityIdentifierDto> identifiers = listIdentifiers(securityId);
                    List<DividendDto> dividends = listDividends(securityId);
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
                            rs.getBigDecimal("market_cap"),
                            identifiers,
                            dividends
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

    public List<DividendDto> listDividends(long securityId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("securityId", securityId);
        return jdbc.query(
                """
                select ex_date, amount, dividend_type, raw_text
                from market.dividend
                where security_id = :securityId
                order by ex_date desc
                """,
                params,
                (rs, rowNum) -> new DividendDto(
                        rs.getObject("ex_date", LocalDate.class),
                        rs.getBigDecimal("amount"),
                        rs.getString("dividend_type"),
                        rs.getString("raw_text")
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

    private static final RowMapper<BarDto> BAR_MAPPER = (rs, rowNum) -> new BarDto(
            rs.getObject("bar_date", LocalDate.class),
            rs.getObject("open", BigDecimal.class),
            rs.getObject("high", BigDecimal.class),
            rs.getObject("low", BigDecimal.class),
            rs.getObject("close", BigDecimal.class),
            rs.getObject("volume") == null ? null : rs.getLong("volume")
    );
}
