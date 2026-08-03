package com.skytrace.backend.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KeycloakRoleConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopeConverter =
            new JwtGrantedAuthoritiesConverter();
    private final String clientId;

    public KeycloakRoleConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>(
                scopeConverter.convert(jwt)
        );
        addRoles(
                authorities,
                rolesFromAccess(jwt.getClaim("realm_access"))
        );

        Object resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess instanceof Map<?, ?> clients) {
            addRoles(
                    authorities,
                    rolesFromAccess(clients.get(clientId))
            );
        }
        return authorities;
    }

    private Collection<?> rolesFromAccess(Object value) {
        if (!(value instanceof Map<?, ?> access)) {
            return List.of();
        }
        Object roles = access.get("roles");
        return roles instanceof Collection<?> collection
                ? collection
                : List.of();
    }

    private void addRoles(
            List<GrantedAuthority> authorities,
            Collection<?> roles) {
        roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isBlank())
                .map(role -> role.startsWith("ROLE_")
                        ? role
                        : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
    }
}
