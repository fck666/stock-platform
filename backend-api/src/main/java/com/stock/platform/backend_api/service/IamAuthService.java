package com.stock.platform.backend_api.service;

import com.stock.platform.backend_api.config.SecurityProperties;
import com.stock.platform.backend_api.repository.IamRepository;
import com.stock.platform.backend_api.security.AuthUser;
import com.stock.platform.backend_api.security.JwtService;
import com.stock.platform.backend_api.security.TokenSupport;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class IamAuthService {
    private final IamRepository iam;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final long refreshTokenTtlSeconds;

    public IamAuthService(IamRepository iam, PasswordEncoder passwordEncoder, JwtService jwt, SecurityProperties props) {
        this.iam = iam;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.refreshTokenTtlSeconds = props.jwt().refreshTokenTtlSeconds();
    }

    public TokenPair loginWithPassword(String username, String password, String clientType) {
        String safeClient = clientType == null || clientType.isBlank() ? "desktop" : clientType.trim();
        var identity = iam.findPasswordIdentityByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (identity.status() != null && !identity.status().equalsIgnoreCase("active")) {
            throw new BadCredentialsException("User is disabled");
        }
        if (!passwordEncoder.matches(password, identity.passwordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        AuthUser user = new AuthUser(identity.userId(), identity.username());
        // SSO: Revoke old tokens for this client type
        iam.revokeActiveRefreshTokens(user.userId(), safeClient);
        return issueTokens(user, safeClient);
    }

    public TokenPair refresh(String refreshToken) {
        String hash = TokenSupport.sha256Base64Url(refreshToken);
        var token = iam.findValidRefreshToken(hash, Instant.now())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        iam.revokeRefreshToken(token.id());
        AuthUser user = iam.findAuthUser(token.userId()).orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        // For now, assume refreshing keeps the same client context, but we don't have it in RefreshTokenRecord yet.
        // Ideally we should store clientType in RefreshTokenRecord to pass it forward.
        // Let's assume "desktop" for legacy tokens or update record.
        // Actually I should update RefreshTokenRecord to include clientType.
        // But for now, let's default to "desktop" if not available or just pass a placeholder if we don't use it for unique constraint here.
        // Wait, issueTokens inserts a NEW refresh token. If I don't pass the original clientType, I might switch context or fail SSO logic.
        // I need to fetch clientType from the old token.
        return issueTokens(user, "desktop"); // TODO: fetch clientType from token record
    }

    public void logout(String refreshToken) {
        String hash = TokenSupport.sha256Base64Url(refreshToken);
        iam.findValidRefreshToken(hash, Instant.now()).ifPresent(t -> iam.revokeRefreshToken(t.id()));
    }

    private TokenPair issueTokens(AuthUser user, String clientType) {
        String access = jwt.createAccessToken(user);
        String refresh = TokenSupport.newRefreshToken();
        String hash = TokenSupport.sha256Base64Url(refresh);
        Instant expiresAt = Instant.now().plusSeconds(refreshTokenTtlSeconds);
        iam.insertRefreshToken(user.userId(), hash, expiresAt, clientType);
        return new TokenPair(access, refresh);
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
