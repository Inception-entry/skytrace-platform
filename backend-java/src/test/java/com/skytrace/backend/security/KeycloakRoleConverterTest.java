package com.skytrace.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter =
            new KeycloakRoleConverter("skytrace-web");

    @Test
    void convertsRealmAndClientRolesToSpringAuthorities() {
        Jwt jwt = jwt(Map.of(
                "scope", "openid profile",
                "realm_access", Map.of(
                        "roles", List.of("viewer", "offline_access")
                ),
                "resource_access", Map.of(
                        "skytrace-web", Map.of(
                                "roles", List.of("operator")
                        )
                )
        ));

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains(
                        "SCOPE_openid",
                        "SCOPE_profile",
                        "ROLE_VIEWER",
                        "ROLE_OPERATOR"
                );
    }

    @Test
    void ignoresMalformedRoleClaims() {
        Jwt jwt = jwt(Map.of(
                "realm_access", "invalid",
                "resource_access", Map.of(
                        "skytrace-web", Map.of("roles", "invalid")
                )
        ));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private Jwt jwt(Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-001")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claims(target -> target.putAll(claims))
                .build();
    }
}
