package com.stock.platform.backend_api.api;

import com.stock.platform.backend_api.api.dto.LoginRequestDto;
import com.stock.platform.backend_api.api.dto.RefreshTokenRequestDto;
import com.stock.platform.backend_api.api.dto.TokenResponseDto;
import com.stock.platform.backend_api.service.IamAuthService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final IamAuthService auth;

    public AuthController(IamAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public TokenResponseDto login(@Valid @RequestBody LoginRequestDto req) {
        var tokens = auth.loginWithPassword(req.username().trim(), req.password());
        return new TokenResponseDto(tokens.accessToken(), tokens.refreshToken());
    }

    @PostMapping("/refresh")
    public TokenResponseDto refresh(@Valid @RequestBody RefreshTokenRequestDto req) {
        var tokens = auth.refresh(req.refreshToken().trim());
        return new TokenResponseDto(tokens.accessToken(), tokens.refreshToken());
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public void logout(@Valid @RequestBody RefreshTokenRequestDto req) {
        auth.logout(req.refreshToken().trim());
    }
}
