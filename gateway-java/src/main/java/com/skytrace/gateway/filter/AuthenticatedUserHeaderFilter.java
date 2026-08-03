package com.skytrace.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Component
public class AuthenticatedUserHeaderFilter
        implements GlobalFilter, Ordered {

    private final String userHeader;
    private final String usernameHeader;
    private final String rolesHeader;

    public AuthenticatedUserHeaderFilter(
            @Value("${gateway.security.user-header:X-Authenticated-User}")
            String userHeader,
            @Value("${gateway.security.username-header:X-Authenticated-Username}")
            String usernameHeader,
            @Value("${gateway.security.roles-header:X-Authenticated-Roles}")
            String rolesHeader) {
        this.userHeader = userHeader;
        this.usernameHeader = usernameHeader;
        this.rolesHeader = rolesHeader;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {
        ServerHttpRequest sanitizedRequest = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(userHeader);
                    headers.remove(usernameHeader);
                    headers.remove(rolesHeader);
                })
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate()
                .request(sanitizedRequest)
                .build();

        return sanitizedExchange.getPrincipal()
                .filter(principal ->
                        principal instanceof Authentication authentication
                                && authentication.isAuthenticated()
                                && !"anonymousUser".equals(
                                        authentication.getName()
                                )
                )
                .map(principal -> {
                    Authentication authentication =
                            (Authentication) principal;
                    String userId = resolveUserId(authentication);
                    String username = resolveUsername(authentication);
                    String roles = authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .filter(authority ->
                                    authority.startsWith("ROLE_")
                            )
                            .map(authority -> authority.substring(5))
                            .sorted()
                            .collect(Collectors.joining(","));
                    ServerHttpRequest authenticatedRequest =
                            sanitizedRequest.mutate()
                                    .headers(headers -> {
                                        setIfPresent(
                                                headers,
                                                userHeader,
                                                userId
                                        );
                                        setIfPresent(
                                                headers,
                                                usernameHeader,
                                                username
                                        );
                                        setIfPresent(
                                                headers,
                                                rolesHeader,
                                                roles
                                        );
                                    })
                                    .build();
                    return sanitizedExchange.mutate()
                            .request(authenticatedRequest)
                            .build();
                })
                .defaultIfEmpty(sanitizedExchange)
                .flatMap(chain::filter);
    }

    private String resolveUserId(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt
                && StringUtils.hasText(jwt.getSubject())) {
            return safeHeaderValue(jwt.getSubject());
        }
        return safeHeaderValue(authentication.getName());
    }

    private String resolveUsername(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString(
                    "preferred_username"
            );
            if (StringUtils.hasText(preferredUsername)) {
                return safeHeaderValue(preferredUsername);
            }
        }
        return safeHeaderValue(authentication.getName());
    }

    private void setIfPresent(
            org.springframework.http.HttpHeaders headers,
            String name,
            String value) {
        if (StringUtils.hasText(value)) {
            headers.set(name, value);
        }
    }

    private String safeHeaderValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String sanitized = value
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        return sanitized.length() > 256
                ? sanitized.substring(0, 256)
                : sanitized;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
