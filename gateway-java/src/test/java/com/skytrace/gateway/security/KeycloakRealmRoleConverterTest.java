package com.skytrace.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter =
            new KeycloakRealmRoleConverter("skytrace-web");

    @Test
    void convertsScopesRealmRolesAndClientRoles() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-001")
                .claim("scope", "profile task:read")
                .claim("realm_access", Map.of(
                        "roles",
                        List.of("OPERATOR", "VIEWER")
                ))
                .claim("resource_access", Map.of(
                        "skytrace-web",
                        Map.of("roles", List.of("task-editor"))
                ))
                .build();

        Collection<String> authorities = converter.convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).contains(
                "SCOPE_profile",
                "SCOPE_task:read",
                "ROLE_OPERATOR",
                "ROLE_VIEWER",
                "ROLE_TASK-EDITOR"
        );
    }
}
