package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.ChangePasswordRequestDto;
import com.stock.platform.backend_api.api.dto.MeDto;
import com.stock.platform.backend_api.security.AuthUser;
import com.stock.platform.backend_api.security.IamAuthorizationService;
import com.stock.platform.backend_api.service.IamUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class MeController {
    private final IamAuthorizationService authz;
    private final IamUserService userService;

    public MeController(IamAuthorizationService authz, IamUserService userService) {
        this.authz = authz;
        this.userService = userService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public MeDto me(Authentication authentication) {
        AuthUser user = (AuthUser) authentication.getPrincipal();
        return new MeDto(
                user.userId(),
                user.username(),
                authz.listRoleCodes(user.userId()),
                authz.listPermissionCodes(user.userId())
        );
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequestDto req) {
        userService.changePassword(req.oldPassword(), req.newPassword());
    }
}
