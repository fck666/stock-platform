package com.stock.platform.backend_api.repository;

import com.stock.platform.backend_api.security.AuthUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IamRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public IamRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRecord> findUserByUsername(String username) {
        String sql = """
                select id, username
                from iam.users
                where username = :username
                """;
        List<UserRecord> rows = jdbc.query(
                sql,
                new MapSqlParameterSource("username", username),
                (rs, i) -> mapUser(rs)
        );
        return rows.stream().findFirst();
    }

    public AuthUser createUser(String username) {
        String sql = """
                insert into iam.users (username)
                values (:username)
                returning id, username
                """;
        return jdbc.queryForObject(
                sql,
                new MapSqlParameterSource("username", username),
                (rs, i) -> new AuthUser(UUID.fromString(rs.getString("id")), rs.getString("username"))
        );
    }

    public void upsertPasswordIdentity(UUID userId, String providerUid, String passwordHash) {
        String sql = """
                insert into iam.identities (user_id, provider, provider_uid, password_hash)
                values (:userId, 'password', :providerUid, :passwordHash)
                on conflict (provider, provider_uid)
                do update set user_id = excluded.user_id, password_hash = excluded.password_hash
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("providerUid", providerUid)
                .addValue("passwordHash", passwordHash)
        );
    }

    public Optional<PasswordIdentity> findPasswordIdentityByUsername(String username) {
        String sql = """
                select i.user_id, u.username, u.status, i.password_hash
                from iam.identities i
                join iam.users u on u.id = i.user_id
                where i.provider = 'password'
                  and i.provider_uid = :username
                """;
        List<PasswordIdentity> rows = jdbc.query(
                sql,
                new MapSqlParameterSource("username", username),
                (rs, i) -> new PasswordIdentity(
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("username"),
                        rs.getString("status"),
                        rs.getString("password_hash")
                )
        );
        return rows.stream().findFirst();
    }

    public void ensureRoleExists(String code, String name) {
        String sql = """
                insert into iam.roles (code, name)
                values (:code, :name)
                on conflict (code) do nothing
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("name", name)
        );
    }

    public void ensurePermissionExists(String code, String name) {
        String sql = """
                insert into iam.permissions (code, name)
                values (:code, :name)
                on conflict (code) do nothing
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("name", name)
        );
    }

    public void ensureRolePermission(String roleCode, String permissionCode) {
        String sql = """
                insert into iam.role_permissions (role_id, permission_id)
                select r.id, p.id
                from iam.roles r, iam.permissions p
                where r.code = :roleCode and p.code = :permissionCode
                on conflict do nothing
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("roleCode", roleCode)
                .addValue("permissionCode", permissionCode)
        );
    }

    public void ensureUserRole(UUID userId, String roleCode) {
        String sql = """
                insert into iam.user_roles (user_id, role_id)
                select :userId, r.id
                from iam.roles r
                where r.code = :roleCode
                on conflict do nothing
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("roleCode", roleCode)
        );
    }

    public List<String> listRoleCodes(UUID userId) {
        String sql = """
                select r.code
                from iam.user_roles ur
                join iam.roles r on r.id = ur.role_id
                where ur.user_id = :userId
                order by r.code
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource("userId", userId), String.class);
    }

    public List<String> listPermissionCodes(UUID userId) {
        String sql = """
                select distinct p.code
                from iam.user_roles ur
                join iam.role_permissions rp on rp.role_id = ur.role_id
                join iam.permissions p on p.id = rp.permission_id
                where ur.user_id = :userId
                order by p.code
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource("userId", userId), String.class);
    }

    public void insertRefreshToken(UUID userId, String tokenHash, Instant expiresAt, String clientType) {
        String sql = """
                insert into iam.refresh_tokens (user_id, token_hash, expires_at, client_type)
                values (:userId, :tokenHash, :expiresAt, :clientType)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tokenHash", tokenHash)
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("clientType", clientType)
        );
    }

    public void revokeActiveRefreshTokens(UUID userId, String clientType) {
        String sql = """
                update iam.refresh_tokens
                set revoked_at = now()
                where user_id = :userId
                  and client_type = :clientType
                  and revoked_at is null
                  and expires_at > now()
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("clientType", clientType)
        );
    }

    public void insertAuditLog(
            UUID actorId,
            String actorUsername,
            UUID targetId,
            String targetUsername,
            String action,
            String detailsJson,
            String ip,
            String ua,
            String requestId,
            String httpMethod,
            String route
    ) {
        // detailsJson 以 JSON 字符串形式传入并 cast 为 jsonb，便于结构化查询与前端展示
        String sql = """
                insert into iam.audit_logs (
                    actor_id, actor_username, target_id, target_username,
                    action, details, ip_address, user_agent,
                    request_id, http_method, route
                )
                values (
                    :actorId, :actorUsername, :targetId, :targetUsername,
                    :action,
                    case when :details is null then null::jsonb else cast(:details as jsonb) end,
                    :ip, :ua,
                    :requestId, :httpMethod, :route
                )
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("actorUsername", actorUsername)
                .addValue("targetId", targetId)
                .addValue("targetUsername", targetUsername)
                .addValue("action", action)
                .addValue("details", detailsJson)
                .addValue("ip", ip)
                .addValue("ua", ua)
                .addValue("requestId", requestId)
                .addValue("httpMethod", httpMethod)
                .addValue("route", route)
        );
    }

    public long countSuperAdmins() {
        String sql = """
                select count(distinct u.id)
                from iam.users u
                join iam.user_roles ur on ur.user_id = u.id
                join iam.roles r on r.id = ur.role_id
                where r.code = 'super_admin'
                  and u.status = 'active'
                """;
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return count != null ? count : 0;
    }

    public void updateUserStatus(UUID userId, String status) {
        String sql = """
                update iam.users
                set status = :status, updated_at = now()
                where id = :userId
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("status", status)
        );
    }

    public void deleteUser(UUID userId) {
        // Cascade delete will handle related tables, but let's be safe and rely on FK cascade
        String sql = "delete from iam.users where id = :userId";
        jdbc.update(sql, new MapSqlParameterSource("userId", userId));
    }
    
    public Optional<RefreshTokenRecord> findValidRefreshToken(String tokenHash, Instant now) {
        String sql = """
                select id, user_id, expires_at
                from iam.refresh_tokens
                where token_hash = :tokenHash
                  and revoked_at is null
                  and expires_at > :now
                """;
        List<RefreshTokenRecord> rows = jdbc.query(
                sql,
                new MapSqlParameterSource()
                        .addValue("tokenHash", tokenHash)
                        .addValue("now", Timestamp.from(now)),
                (rs, i) -> new RefreshTokenRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getTimestamp("expires_at").toInstant()
                )
        );
        return rows.stream().findFirst();
    }

    public void revokeRefreshToken(UUID id) {
        String sql = """
                update iam.refresh_tokens
                set revoked_at = now()
                where id = :id
                """;
        jdbc.update(sql, new MapSqlParameterSource("id", id));
    }

    public Optional<AuthUser> findAuthUser(UUID userId) {
        String sql = """
                select id, username
                from iam.users
                where id = :id
                  and status = 'active'
                """;
        List<AuthUser> rows = jdbc.query(
                sql,
                new MapSqlParameterSource("id", userId),
                (rs, i) -> new AuthUser(UUID.fromString(rs.getString("id")), rs.getString("username"))
        );
        return rows.stream().findFirst();
    }

    public List<UserWithRoles> listUsers() {
        String sql = """
                select u.id, u.username, u.status, coalesce(string_agg(r.code, ',' order by r.code), '') as roles
                from iam.users u
                left join iam.user_roles ur on ur.user_id = u.id
                left join iam.roles r on r.id = ur.role_id
                group by u.id, u.username, u.status
                order by u.username
                """;
        return jdbc.query(sql, new MapSqlParameterSource(), (rs, i) -> {
            String rolesCsv = rs.getString("roles");
            List<String> roles = rolesCsv == null || rolesCsv.isBlank()
                    ? List.of()
                    : Arrays.stream(rolesCsv.split(",")).filter(s -> !s.isBlank()).toList();
            return new UserWithRoles(UUID.fromString(rs.getString("id")), rs.getString("username"), rs.getString("status"), roles);
        });
    }

    public void replaceUserRoles(UUID userId, List<String> roleCodes) {
        jdbc.update(
                "delete from iam.user_roles where user_id = :userId",
                new MapSqlParameterSource("userId", userId)
        );
        if (roleCodes == null || roleCodes.isEmpty()) return;

        String sql = """
                insert into iam.user_roles (user_id, role_id)
                select :userId, r.id
                from iam.roles r
                where r.code = :roleCode
                on conflict do nothing
                """;
        List<MapSqlParameterSource> batch = new ArrayList<>();
        for (String roleCode : roleCodes) {
            if (roleCode == null || roleCode.isBlank()) continue;
            batch.add(new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("roleCode", roleCode.trim())
            );
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch.toArray(new MapSqlParameterSource[0]));
        }
    }

    public List<RoleWithPermissions> listRoles() {
        String sql = """
                select r.code, r.name, coalesce(string_agg(p.code, ',' order by p.code), '') as permissions
                from iam.roles r
                left join iam.role_permissions rp on rp.role_id = r.id
                left join iam.permissions p on p.id = rp.permission_id
                group by r.code, r.name
                order by r.code
                """;
        return jdbc.query(sql, new MapSqlParameterSource(), (rs, i) -> {
            String permissionsCsv = rs.getString("permissions");
            List<String> permissions = permissionsCsv == null || permissionsCsv.isBlank()
                    ? List.of()
                    : Arrays.stream(permissionsCsv.split(",")).filter(s -> !s.isBlank()).toList();
            return new RoleWithPermissions(rs.getString("code"), rs.getString("name"), permissions);
        });
    }

    private static UserRecord mapUser(ResultSet rs) throws SQLException {
        return new UserRecord(
                UUID.fromString(rs.getString("id")),
                rs.getString("username")
        );
    }

    public record UserRecord(UUID id, String username) {
    }

    public record UserWithRoles(UUID userId, String username, String status, List<String> roles) {
    }

    public record RoleWithPermissions(String code, String name, List<String> permissions) {
    }

    public record PasswordIdentity(UUID userId, String username, String status, String passwordHash) {
    }

    public record RefreshTokenRecord(UUID id, UUID userId, Instant expiresAt) {
    }

    public List<AuditLogRecord> listAuditLogs(int limit) {
        String sql = """
                select id, actor_id, actor_username, target_id, target_username, action, details::text as details,
                       ip_address, user_agent, request_id, http_method, route, status_code, latency_ms, created_at
                from iam.audit_logs
                order by created_at desc
                limit :limit
                """;
        return jdbc.query(sql, new MapSqlParameterSource("limit", limit), (rs, i) -> new AuditLogRecord(
                UUID.fromString(rs.getString("id")),
                rs.getString("actor_id") == null ? null : UUID.fromString(rs.getString("actor_id")),
                rs.getString("actor_username"),
                rs.getString("target_id") == null ? null : UUID.fromString(rs.getString("target_id")),
                rs.getString("target_username"),
                rs.getString("action"),
                rs.getString("details"),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                rs.getString("request_id"),
                rs.getString("http_method"),
                rs.getString("route"),
                rs.getObject("status_code") == null ? null : rs.getInt("status_code"),
                rs.getObject("latency_ms") == null ? null : rs.getInt("latency_ms"),
                rs.getTimestamp("created_at").toInstant()
        ));
    }

    public record AuditLogRecord(
            UUID id,
            UUID actorId,
            String actorUsername,
            UUID targetId,
            String targetUsername,
            String action,
            String details,
            String ipAddress,
            String userAgent,
            String requestId,
            String httpMethod,
            String route,
            Integer statusCode,
            Integer latencyMs,
            Instant createdAt
    ) {
    }
}
