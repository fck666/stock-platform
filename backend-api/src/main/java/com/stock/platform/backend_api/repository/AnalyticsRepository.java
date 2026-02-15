package com.stock.platform.backend_api.repository;

import com.stock.platform.backend_api.api.dto.AnalyticsSeriesPointDto;
import com.stock.platform.backend_api.api.dto.AnalyticsTopApiDto;
import com.stock.platform.backend_api.api.dto.AnalyticsTopItemDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
/**
 * 轻量行为分析数据访问层。
 *
 * - analytics.page_views：页面浏览记录（由前端路由上报）
 * - analytics.api_calls：接口调用记录（由后端过滤器自动采集）
 *
 * 本模块面向“管理员看板”的聚合查询，避免在业务系统内重做完整埋点平台能力。
 */
public class AnalyticsRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertPageView(UUID userId, String username, String path, String title, Instant now) {
        String sql = """
                insert into analytics.page_views (user_id, username, path, title, created_at)
                values (:userId, :username, :path, :title, :createdAt)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("username", username)
                .addValue("path", path)
                .addValue("title", title)
                .addValue("createdAt", Timestamp.from(now))
        );
    }

    public void insertApiCall(UUID userId, String username, String method, String path, int statusCode, int latencyMs, Instant now) {
        String sql = """
                insert into analytics.api_calls (user_id, username, method, path, status_code, latency_ms, created_at)
                values (:userId, :username, :method, :path, :statusCode, :latencyMs, :createdAt)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("username", username)
                .addValue("method", method)
                .addValue("path", path)
                .addValue("statusCode", statusCode)
                .addValue("latencyMs", latencyMs)
                .addValue("createdAt", Timestamp.from(now))
        );
    }

    public List<AnalyticsSeriesPointDto> getDailyPageViews(int days) {
        String sql = """
                select (created_at at time zone 'UTC')::date as day, count(*) as cnt
                from analytics.page_views
                where created_at >= now() - (:days || ' days')::interval
                group by day
                order by day
                """;
        return jdbc.query(sql, new MapSqlParameterSource("days", days), (rs, i) ->
                new AnalyticsSeriesPointDto(rs.getObject("day", LocalDate.class), rs.getLong("cnt")));
    }

    public List<AnalyticsSeriesPointDto> getDailyApiCalls(int days) {
        String sql = """
                select (created_at at time zone 'UTC')::date as day, count(*) as cnt
                from analytics.api_calls
                where created_at >= now() - (:days || ' days')::interval
                group by day
                order by day
                """;
        return jdbc.query(sql, new MapSqlParameterSource("days", days), (rs, i) ->
                new AnalyticsSeriesPointDto(rs.getObject("day", LocalDate.class), rs.getLong("cnt")));
    }

    public List<AnalyticsTopItemDto> getTopPages(int days, int limit) {
        String sql = """
                select path as key, count(*) as cnt
                from analytics.page_views
                where created_at >= now() - (:days || ' days')::interval
                group by path
                order by cnt desc
                limit :limit
                """;
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("days", days)
                .addValue("limit", limit), (rs, i) ->
                new AnalyticsTopItemDto(rs.getString("key"), rs.getLong("cnt")));
    }

    public List<AnalyticsTopApiDto> getTopApis(int days, int limit) {
        String sql = """
                select method, path,
                       count(*) as cnt,
                       sum(case when status_code >= 400 then 1 else 0 end) as err_cnt,
                       coalesce(percentile_cont(0.95) within group (order by latency_ms), 0)::bigint as p95_ms
                from analytics.api_calls
                where created_at >= now() - (:days || ' days')::interval
                group by method, path
                order by cnt desc
                limit :limit
                """;
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("days", days)
                .addValue("limit", limit), (rs, i) ->
                new AnalyticsTopApiDto(
                        rs.getString("method"),
                        rs.getString("path"),
                        rs.getLong("cnt"),
                        rs.getLong("err_cnt"),
                        rs.getLong("p95_ms")
                ));
    }

    public List<AnalyticsTopItemDto> getTopUsersByApiCalls(int days, int limit) {
        String sql = """
                select coalesce(username, 'unknown') as key, count(*) as cnt
                from analytics.api_calls
                where created_at >= now() - (:days || ' days')::interval
                group by key
                order by cnt desc
                limit :limit
                """;
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("days", days)
                .addValue("limit", limit), (rs, i) ->
                new AnalyticsTopItemDto(rs.getString("key"), rs.getLong("cnt")));
    }
}
