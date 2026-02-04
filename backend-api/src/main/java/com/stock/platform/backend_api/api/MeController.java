package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.MeDto;
import com.stock.platform.backend_api.security.AuthUser;
import com.stock.platform.backend_api.security.IamAuthorizationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {
    private final IamAuthorizationService authz;

    public MeController(IamAuthorizationService authz) {
        this.authz = authz;
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
}
