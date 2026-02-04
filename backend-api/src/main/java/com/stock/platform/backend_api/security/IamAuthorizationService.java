package com.stock.platform.backend_api.security;

import com.stock.platform.backend_api.repository.IamRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class IamAuthorizationService {
    private final IamRepository iam;

    public IamAuthorizationService(IamRepository iam) {
        this.iam = iam;
    }

    public List<String> listRoleCodes(UUID userId) {
        return iam.listRoleCodes(userId);
    }

    public List<String> listPermissionCodes(UUID userId) {
        return iam.listPermissionCodes(userId);
    }

    public List<GrantedAuthority> buildAuthorities(UUID userId) {
        List<String> roles = listRoleCodes(userId);
        List<String> permissions = listPermissionCodes(userId);
        List<GrantedAuthority> authorities = new ArrayList<>(roles.size() + permissions.size());
        for (String r : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()));
        }
        for (String p : permissions) {
            authorities.add(new SimpleGrantedAuthority(p));
        }
        return authorities;
    }
}
