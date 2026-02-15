package com.stock.platform.backend_api.service;

import com.stock.platform.backend_api.config.SecurityProperties;
import com.stock.platform.backend_api.repository.IamRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class IamBootstrap implements ApplicationRunner {
    private final IamRepository iam;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties props;

    public IamBootstrap(IamRepository iam, PasswordEncoder passwordEncoder, SecurityProperties props) {
        this.iam = iam;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedRolesAndPermissions();
        seedInitAdmin();
    }

    private void seedRolesAndPermissions() {
        iam.ensureRoleExists("super_admin", "超级管理员");
        iam.ensureRoleExists("admin", "管理员");
        iam.ensureRoleExists("manager", "经理");
        iam.ensureRoleExists("viewer", "观察员");
        iam.ensureRoleExists("user", "普通用户");

        iam.ensurePermissionExists("data.sync.execute", "执行数据同步");
        iam.ensurePermissionExists("admin.stock.write", "管理股票");
        iam.ensurePermissionExists("admin.index.write", "管理指数");
        iam.ensurePermissionExists("iam.manage", "管理账号与权限");

        // Super Admin has all permissions (handled via logic or explicit grant)
        iam.ensureRolePermission("super_admin", "iam.manage");
        iam.ensureRolePermission("super_admin", "data.sync.execute");
        iam.ensureRolePermission("super_admin", "admin.stock.write");
        iam.ensureRolePermission("super_admin", "admin.index.write");

        iam.ensureRolePermission("admin", "data.sync.execute");
        iam.ensureRolePermission("admin", "admin.stock.write");
        iam.ensureRolePermission("admin", "admin.index.write");
        // Admin CANNOT manage IAM in the same way super_admin does, but they can view users.
        // We might need a separate permission for "iam.view" or just use "iam.manage" with service-level checks.
        // For now, let's NOT give 'iam.manage' to 'admin' role in DB, but rely on service check?
        // Wait, Controller has @PreAuthorize("hasAuthority('iam.manage')").
        // If I want Admin to access IamAdminController, I must give them 'iam.manage'.
        // Service level check `isAdmin` will restrict what they can do.
        iam.ensureRolePermission("admin", "iam.manage");
    }

    private void seedInitAdmin() {
        // Ensure 'fcc' is super_admin if it exists or create it
        String fccUser = "fcc";
        // ... (existing logic) ...
        // Actually, the user asked to initialize 'fcc' as super_admin.
        // I should add explicit logic for 'fcc'.
        
        createOrUpdateSuperAdmin("fcc", "123456"); // Default password, should be changed or set via env

        SecurityProperties.InitAdmin init = props.initAdmin();
        if (init != null && init.username() != null && !init.username().isBlank()) {
             createOrUpdateSuperAdmin(init.username(), init.password());
        }
    }

    private void createOrUpdateSuperAdmin(String username, String password) {
        if (username == null || username.isBlank()) return;
        
        var user = iam.findUserByUsername(username).map(u -> new java.util.AbstractMap.SimpleEntry<>(u.id(), u.username()))
                .orElseGet(() -> {
                    var created = iam.createUser(username);
                    return new java.util.AbstractMap.SimpleEntry<>(created.userId(), created.username());
                });

        boolean hasIdentity = iam.findPasswordIdentityByUsername(username).isPresent();
        if (!hasIdentity && password != null && !password.isBlank()) {
            String hash = passwordEncoder.encode(password);
            iam.upsertPasswordIdentity(user.getKey(), username, hash);
        }
        iam.ensureUserRole(user.getKey(), "super_admin");
    }
}
