package com.skytrace.gateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts standard OAuth scopes and Keycloak realm/client roles into Spring
 * Security authorities.
 */
public class KeycloakRealmRoleConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopeConverter =
            new JwtGrantedAuthoritiesConverter();
    private final String clientId;

    public KeycloakRealmRoleConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        Collection<GrantedAuthority> scopeAuthorities =
                scopeConverter.convert(jwt);
        if (scopeAuthorities != null) {
            authorities.addAll(scopeAuthorities);
        }

        addRoles(authorities, extractRoles(jwt.getClaim("realm_access")));

        if (StringUtils.hasText(clientId)) {
            Object resourceAccessClaim = jwt.getClaim("resource_access");
            if (resourceAccessClaim instanceof Map<?, ?> resourceAccess) {
                addRoles(
                        authorities,
                        extractRoles(resourceAccess.get(clientId))
                );
            }
        }

        return Collections.unmodifiableSet(authorities);
    }

    private Collection<?> extractRoles(Object accessClaim) {
        if (!(accessClaim instanceof Map<?, ?> access)) {
            return Collections.emptyList();
        }
        Object roles = access.get("roles");
        return roles instanceof Collection<?> collection
                ? collection
                : Collections.emptyList();
    }

    private void addRoles(
            Set<GrantedAuthority> authorities,
            Collection<?> roles) {
        roles.stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .map(this::toRoleAuthority)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
    }

    private String toRoleAuthority(String role) {
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_")
                ? normalized
                : "ROLE_" + normalized;
    }
}
