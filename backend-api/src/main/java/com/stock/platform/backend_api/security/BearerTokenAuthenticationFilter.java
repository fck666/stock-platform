package com.stock.platform.backend_api.security;

import com.stock.platform.backend_api.repository.IamRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
/**
 * Spring Security Filter that authenticates requests based on the "Authorization: Bearer <token>" header.
 * It parses the JWT, validates it, loads the user details, and sets the SecurityContext.
 */
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final IamRepository iam;
    private final IamAuthorizationService authz;

    public BearerTokenAuthenticationFilter(JwtService jwt, IamRepository iam, IamAuthorizationService authz) {
        this.jwt = jwt;
        this.iam = iam;
        this.authz = authz;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Parse and validate the token
                jwt.parseAccessToken(token).ifPresent(subject -> {
                    try {
                        UUID userId = UUID.fromString(subject.userId());
                        // Load user and set authentication context
                        iam.findAuthUser(userId).ifPresent(user -> {
                            var authorities = authz.buildAuthorities(userId);
                            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
                    } catch (IllegalArgumentException ignored) {
                        // Invalid UUID format or user not found
                    }
                });
            }
        }
        filterChain.doFilter(request, response);
    }
}
