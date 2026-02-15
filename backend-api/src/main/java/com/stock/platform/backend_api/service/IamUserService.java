package com.stock.platform.backend_api.service;

import com.stock.platform.backend_api.api.dto.AuditLogDto;
import com.stock.platform.backend_api.api.dto.CreateUserRequestDto;
import com.stock.platform.backend_api.repository.IamRepository;
import com.stock.platform.backend_api.security.AuthUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IamUserService {
    private final IamRepository iam;
    private final PasswordEncoder passwordEncoder;

    public IamUserService(IamRepository iam, PasswordEncoder passwordEncoder) {
        this.iam = iam;
        this.passwordEncoder = passwordEncoder;
    }

    private AuthUser getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AuthUser u) return u;
        throw new AccessDeniedException("Not authenticated");
    }

    private IamRepository.UserWithRoles findUserOrThrow(UUID userId) {
        return iam.listUsers().stream()
                .filter(u -> u.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private boolean hasRole(IamRepository.UserWithRoles user, String roleCode) {
        return user.roles() != null && user.roles().contains(roleCode);
    }

    private boolean isSuperAdmin(IamRepository.UserWithRoles user) {
        return hasRole(user, "super_admin");
    }

    private boolean isAdmin(IamRepository.UserWithRoles user) {
        return hasRole(user, "admin");
    }

    /**
     * Check if actor can manage target.
     * Rule:
     * - SUPER_ADMIN can manage ANYONE.
     * - ADMIN can manage NON-ADMIN/NON-SUPER_ADMIN.
     * - Self management is restricted in some cases (e.g. can't delete self, can't change own role).
     */
    private void checkCanManage(IamRepository.UserWithRoles actor, IamRepository.UserWithRoles target) {
        if (isSuperAdmin(actor)) {
            // Super Admin can manage anyone
            return;
        }
        if (isAdmin(actor)) {
            if (isSuperAdmin(target) || isAdmin(target)) {
                if (actor.userId().equals(target.userId())) return;
                throw new AccessDeniedException("Admins cannot manage other Admins or Super Admins");
            }
            return;
        }
        throw new AccessDeniedException("Insufficient permissions to manage users");
    }

    @Transactional
    public void createUser(CreateUserRequestDto req) {
        AuthUser actorAuth = getCurrentUser();
        IamRepository.UserWithRoles actor = findUserOrThrow(actorAuth.userId());

        // Only Admin/SuperAdmin can create users
        if (!isAdmin(actor) && !isSuperAdmin(actor)) {
            throw new AccessDeniedException("Only admins can create users");
        }

        // Check duplicate
        if (iam.findUserByUsername(req.username()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Validate roles assignment
        if (req.roles() != null) {
            for (String role : req.roles()) {
                if (("super_admin".equals(role) || "admin".equals(role)) && !isSuperAdmin(actor)) {
                    throw new AccessDeniedException("Only Super Admin can assign admin roles");
                }
            }
        }

        AuthUser newUser = iam.createUser(req.username());
        String hash = passwordEncoder.encode(req.password());
        iam.upsertPasswordIdentity(newUser.userId(), req.username(), hash);

        if (req.roles() != null && !req.roles().isEmpty()) {
            iam.replaceUserRoles(newUser.userId(), req.roles());
        } else {
            iam.ensureUserRole(newUser.userId(), "viewer"); // Default role
        }

        iam.insertAuditLog(actor.userId(), actor.username(), newUser.userId(), newUser.username(), "CREATE_USER", "Created user " + req.username(), "unknown", "unknown");
    }

    @Transactional
    public void updateUserRoles(UUID userId, List<String> roles) {
        AuthUser actorAuth = getCurrentUser();
        IamRepository.UserWithRoles actor = findUserOrThrow(actorAuth.userId());
        IamRepository.UserWithRoles target = findUserOrThrow(userId);

        checkCanManage(actor, target);

        // Self-protection: restrictions on modifying own roles
        if (actor.userId().equals(target.userId())) {
            // Prevent removing Super Admin role from self if it's currently held
            if (isSuperAdmin(actor) && !roles.contains("super_admin")) {
                throw new AccessDeniedException("Cannot remove your own Super Admin role.");
            }
            // Allow other changes (e.g. adding/removing secondary roles) as long as critical access is maintained
        }

        // Role assignment restriction
        if (!isSuperAdmin(actor)) {
            // Non-super-admin cannot assign super_admin or admin roles
            for (String role : roles) {
                if ("super_admin".equals(role) || "admin".equals(role)) {
                    throw new AccessDeniedException("Only Super Admin can assign admin roles");
                }
            }
        }

        // Last Super Admin protection
        if (isSuperAdmin(target) && (!roles.contains("super_admin"))) {
            long superAdminCount = iam.countSuperAdmins();
            if (superAdminCount <= 1) {
                throw new IllegalArgumentException("Cannot remove the last Super Admin role");
            }
        }

        iam.replaceUserRoles(userId, roles);
        iam.insertAuditLog(actor.userId(), actor.username(), target.userId(), target.username(), "UPDATE_ROLES", "Roles: " + roles, "unknown", "unknown");
    }

    @Transactional
    public void updateUserStatus(UUID userId, String status) {
        AuthUser actorAuth = getCurrentUser();
        IamRepository.UserWithRoles actor = findUserOrThrow(actorAuth.userId());
        IamRepository.UserWithRoles target = findUserOrThrow(userId);

        checkCanManage(actor, target);

        // Self-protection
        if (actor.userId().equals(target.userId())) {
             throw new AccessDeniedException("Cannot change your own status.");
        }
        
        // Last Super Admin protection
        if (isSuperAdmin(target) && !"active".equalsIgnoreCase(status)) {
             long superAdminCount = iam.countSuperAdmins();
             if (superAdminCount <= 1) {
                 throw new IllegalArgumentException("Cannot disable the last Super Admin");
             }
        }

        iam.updateUserStatus(userId, status);
        if (!"active".equalsIgnoreCase(status)) {
            iam.revokeActiveRefreshTokens(userId, "desktop"); // Revoke all sessions?
            // Actually revokeActiveRefreshTokens takes clientType.
            // Maybe we should revoke ALL tokens for ALL clients.
            // For now, let's revoke for "desktop" as it is default.
            // TODO: revoke all client types
        }
        iam.insertAuditLog(actor.userId(), actor.username(), target.userId(), target.username(), "UPDATE_STATUS", "Status: " + status, "unknown", "unknown");
    }

    @Transactional
    public void resetPassword(UUID userId, String adminPassword, String newPassword) {
        AuthUser actorAuth = getCurrentUser();
        IamRepository.UserWithRoles actor = findUserOrThrow(actorAuth.userId());
        IamRepository.UserWithRoles target = findUserOrThrow(userId);

        checkCanManage(actor, target);

        var adminIdentity = iam.findPasswordIdentityByUsername(actorAuth.username())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordEncoder.matches(adminPassword, adminIdentity.passwordHash())) {
            throw new IllegalArgumentException("Invalid admin password");
        }

        String hash = passwordEncoder.encode(newPassword);
        iam.upsertPasswordIdentity(userId, target.username(), hash);
        
        // Force logout target
        iam.revokeActiveRefreshTokens(userId, "desktop");

        iam.insertAuditLog(actor.userId(), actor.username(), target.userId(), target.username(), "RESET_PASSWORD", "Reset password", "unknown", "unknown");
    }
    
    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        AuthUser actorAuth = getCurrentUser();
        // Self change
        var identity = iam.findPasswordIdentityByUsername(actorAuth.username())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (!passwordEncoder.matches(oldPassword, identity.passwordHash())) {
            throw new IllegalArgumentException("Invalid old password");
        }
        
        String hash = passwordEncoder.encode(newPassword);
        iam.upsertPasswordIdentity(actorAuth.userId(), actorAuth.username(), hash);
        
        // Revoke other sessions? Maybe optional.
        // For security, good to revoke others.
        iam.revokeActiveRefreshTokens(actorAuth.userId(), "desktop");
        
        iam.insertAuditLog(actorAuth.userId(), actorAuth.username(), actorAuth.userId(), actorAuth.username(), "CHANGE_PASSWORD", "Changed own password", "unknown", "unknown");
    }

    @Transactional
    public void deleteUser(UUID userId) {
        AuthUser actorAuth = getCurrentUser();
        IamRepository.UserWithRoles actor = findUserOrThrow(actorAuth.userId());
        IamRepository.UserWithRoles target = findUserOrThrow(userId);

        checkCanManage(actor, target);

        // Self-protection
        if (actor.userId().equals(target.userId())) {
            throw new AccessDeniedException("Cannot delete yourself.");
        }

        // Last Super Admin protection
        if (isSuperAdmin(target)) {
            long superAdminCount = iam.countSuperAdmins();
            if (superAdminCount <= 1) {
                throw new IllegalArgumentException("Cannot delete the last Super Admin");
            }
        }

        iam.deleteUser(userId);
        iam.insertAuditLog(actor.userId(), actor.username(), target.userId(), target.username(), "DELETE_USER", "Deleted user", "unknown", "unknown");
    }

    public List<AuditLogDto> listAuditLogs(int limit) {
        AuthUser actorAuth = getCurrentUser();
        IamRepository.UserWithRoles actor = findUserOrThrow(actorAuth.userId());
        if (!isSuperAdmin(actor)) {
             throw new AccessDeniedException("Only Super Admin can view audit logs");
        }
        return iam.listAuditLogs(limit).stream()
                .map(r -> new AuditLogDto(
                        r.id(),
                        r.actorId(),
                        r.actorUsername(),
                        r.targetId(),
                        r.targetUsername(),
                        r.action(),
                        r.details(),
                        r.ipAddress(),
                        r.userAgent(),
                        r.createdAt()
                ))
                .toList();
    }
}
