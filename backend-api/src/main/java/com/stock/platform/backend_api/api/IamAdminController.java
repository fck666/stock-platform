package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.*;
import com.stock.platform.backend_api.repository.IamRepository;
import com.stock.platform.backend_api.service.IamUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/iam")
@PreAuthorize("hasAuthority('iam.manage')")
public class IamAdminController {
    private final IamRepository iam;
    private final IamUserService userService;

    public IamAdminController(IamRepository iam, IamUserService userService) {
        this.iam = iam;
        this.userService = userService;
    }

    @GetMapping("/users")
    public List<AdminUserDto> listUsers() {
        return iam.listUsers().stream()
                .map(u -> new AdminUserDto(u.userId(), u.username(), u.status(), u.roles()))
                .toList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public void createUser(@Valid @RequestBody CreateUserRequestDto req) {
        userService.createUser(req);
    }

    @PutMapping("/users/{userId}/roles")
    public void replaceUserRoles(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRolesRequestDto req) {
        userService.updateUserRoles(userId, req.roles());
    }

    @PutMapping("/users/{userId}/status")
    public void updateUserStatus(@PathVariable UUID userId, @Valid @RequestBody UpdateUserStatusRequestDto req) {
        userService.updateUserStatus(userId, req.status());
    }

    @PostMapping("/users/{userId}/reset-password")
    public void resetPassword(@PathVariable UUID userId, @Valid @RequestBody ResetPasswordRequestDto req) {
        userService.resetPassword(userId, req.adminPassword(), req.newPassword());
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
    }

    @GetMapping("/audit-logs")
    public List<AuditLogDto> listAuditLogs(@RequestParam(defaultValue = "100") int limit) {
        return userService.listAuditLogs(limit);
    }

    @GetMapping("/roles")
    public List<RoleDto> listRoles() {
        return iam.listRoles().stream()
                .map(r -> new RoleDto(r.code(), r.name(), r.permissions()))
                .toList();
    }
}
