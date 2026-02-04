package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.AdminUserDto;
import com.stock.platform.backend_api.api.dto.RoleDto;
import com.stock.platform.backend_api.api.dto.UpdateUserRolesRequestDto;
import com.stock.platform.backend_api.repository.IamRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/iam")
@PreAuthorize("hasAuthority('iam.manage')")
public class IamAdminController {
    private final IamRepository iam;

    public IamAdminController(IamRepository iam) {
        this.iam = iam;
    }

    @GetMapping("/users")
    public List<AdminUserDto> listUsers() {
        return iam.listUsers().stream()
                .map(u -> new AdminUserDto(u.userId(), u.username(), u.roles()))
                .toList();
    }

    @PutMapping("/users/{userId}/roles")
    public void replaceUserRoles(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRolesRequestDto req) {
        iam.replaceUserRoles(userId, req.roles());
    }

    @GetMapping("/roles")
    public List<RoleDto> listRoles() {
        return iam.listRoles().stream()
                .map(r -> new RoleDto(r.code(), r.name(), r.permissions()))
                .toList();
    }
}
