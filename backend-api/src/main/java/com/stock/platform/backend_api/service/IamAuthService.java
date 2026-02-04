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
import java.util.UUID;

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

    public TokenPair loginWithPassword(String username, String password) {
        var identity = iam.findPasswordIdentityByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        if (!passwordEncoder.matches(password, identity.passwordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        AuthUser user = new AuthUser(identity.userId(), identity.username());
        return issueTokens(user);
    }

    public TokenPair refresh(String refreshToken) {
        String hash = TokenSupport.sha256Base64Url(refreshToken);
        var token = iam.findValidRefreshToken(hash, Instant.now())
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        iam.revokeRefreshToken(token.id());
        AuthUser user = iam.findAuthUser(token.userId()).orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        String hash = TokenSupport.sha256Base64Url(refreshToken);
        iam.findValidRefreshToken(hash, Instant.now()).ifPresent(t -> iam.revokeRefreshToken(t.id()));
    }

    private TokenPair issueTokens(AuthUser user) {
        String access = jwt.createAccessToken(user);
        String refresh = TokenSupport.newRefreshToken();
        String hash = TokenSupport.sha256Base64Url(refresh);
        Instant expiresAt = Instant.now().plusSeconds(refreshTokenTtlSeconds);
        iam.insertRefreshToken(user.userId(), hash, expiresAt);
        return new TokenPair(access, refresh);
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }
}
