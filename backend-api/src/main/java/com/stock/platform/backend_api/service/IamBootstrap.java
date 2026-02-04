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
        iam.ensureRoleExists("admin", "管理员");
        iam.ensureRoleExists("user", "普通用户");

        iam.ensurePermissionExists("data.sync.execute", "执行数据同步");
        iam.ensurePermissionExists("admin.stock.write", "管理股票");
        iam.ensurePermissionExists("admin.index.write", "管理指数");
        iam.ensurePermissionExists("iam.manage", "管理账号与权限");

        iam.ensureRolePermission("admin", "data.sync.execute");
        iam.ensureRolePermission("admin", "admin.stock.write");
        iam.ensureRolePermission("admin", "admin.index.write");
        iam.ensureRolePermission("admin", "iam.manage");
    }

    private void seedInitAdmin() {
        SecurityProperties.InitAdmin init = props.initAdmin();
        if (init == null) return;
        String username = init.username() == null ? "" : init.username().trim();
        String password = init.password() == null ? "" : init.password();
        if (username.isBlank() || password.isBlank()) return;

        var user = iam.findUserByUsername(username).map(u -> new java.util.AbstractMap.SimpleEntry<>(u.id(), u.username()))
                .orElseGet(() -> {
                    var created = iam.createUser(username);
                    return new java.util.AbstractMap.SimpleEntry<>(created.userId(), created.username());
                });

        boolean hasIdentity = iam.findPasswordIdentityByUsername(username).isPresent();
        if (init.forceResetPassword() || !hasIdentity) {
            String hash = passwordEncoder.encode(password);
            iam.upsertPasswordIdentity(user.getKey(), username, hash);
        }
        iam.ensureUserRole(user.getKey(), "admin");
    }
}
