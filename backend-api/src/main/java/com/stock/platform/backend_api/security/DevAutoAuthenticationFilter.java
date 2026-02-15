package com.stock.platform.backend_api.security;

import com.stock.platform.backend_api.config.SecurityProperties;
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
import java.util.Arrays;
import java.util.List;

@Component
public class DevAutoAuthenticationFilter extends OncePerRequestFilter {
    private final SecurityProperties props;
    private final IamRepository iam;
    private final IamAuthorizationService authz;

    public DevAutoAuthenticationFilter(SecurityProperties props, IamRepository iam, IamAuthorizationService authz) {
        this.props = props;
        this.iam = iam;
        this.authz = authz;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        SecurityProperties.DevAuth dev = props.devAuth();
        if (dev != null && dev.enabled() && SecurityContextHolder.getContext().getAuthentication() == null) {
            String authorization = request.getHeader("Authorization");
            boolean missingAuthHeader = authorization == null || authorization.isBlank();
            if (!dev.onlyIfMissingAuthorization() || missingAuthHeader) {
                String username = dev.username() == null ? "" : dev.username().trim();
                if (!username.isBlank()) {
                    AuthUser user = ensureDevUser(username, dev);
                    var authorities = authz.buildAuthorities(user.userId());
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private AuthUser ensureDevUser(String username, SecurityProperties.DevAuth dev) {
        var existing = iam.findUserByUsername(username);
        if (existing.isPresent()) {
            return new AuthUser(existing.get().id(), existing.get().username());
        }
        if (!dev.autoCreateUser()) {
            throw new IllegalStateException("Dev auth user not found: " + username);
        }
        AuthUser created = iam.createUser(username);
        assignRoles(created.userId(), dev.roles());
        return created;
    }

    private void assignRoles(java.util.UUID userId, String rolesCsv) {
        List<String> roles = rolesCsv == null || rolesCsv.isBlank()
                ? List.of()
                : Arrays.stream(rolesCsv.split(","))
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        if (roles.isEmpty()) return;
        for (String role : roles) {
            iam.ensureUserRole(userId, role);
        }
    }
}
