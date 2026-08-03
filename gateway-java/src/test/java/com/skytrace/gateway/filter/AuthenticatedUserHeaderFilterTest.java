package com.skytrace.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserHeaderFilterTest {

    private final AuthenticatedUserHeaderFilter filter =
            new AuthenticatedUserHeaderFilter(
                    "X-Authenticated-User",
                    "X-Authenticated-Username",
                    "X-Authenticated-Roles"
            );

    @Test
    void removesForgedIdentityHeadersForAnonymousRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/tasks")
                        .header("X-Authenticated-User", "forged-user")
                        .header("X-Authenticated-Username", "forged-name")
                        .header("X-Authenticated-Roles", "ADMIN")
        );
        AtomicReference<ServerWebExchange> forwarded =
                new AtomicReference<>();
        AtomicInteger invocationCount = new AtomicInteger();

        filter.filter(exchange, capturedExchange -> {
            invocationCount.incrementAndGet();
            forwarded.set(capturedExchange);
            return Mono.empty();
        }).block();

        assertThat(invocationCount).hasValue(1);
        assertThat(forwarded.get().getRequest().getHeaders()
                .containsKey("X-Authenticated-User")).isFalse();
        assertThat(forwarded.get().getRequest().getHeaders()
                .containsKey("X-Authenticated-Username")).isFalse();
        assertThat(forwarded.get().getRequest().getHeaders()
                .containsKey("X-Authenticated-Roles")).isFalse();
    }

    @Test
    void writesVerifiedJwtIdentityHeaders() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-001")
                .claim("preferred_username", "pilot-one")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
        );
        ServerWebExchange exchange = withPrincipal(
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/tasks")
                ),
                authentication
        );
        AtomicReference<ServerWebExchange> forwarded =
                new AtomicReference<>();
        AtomicInteger invocationCount = new AtomicInteger();

        filter.filter(exchange, capturedExchange -> {
            invocationCount.incrementAndGet();
            forwarded.set(capturedExchange);
            return Mono.empty();
        }).block();

        assertThat(invocationCount).hasValue(1);
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst("X-Authenticated-User")).isEqualTo("user-001");
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst("X-Authenticated-Username")).isEqualTo("pilot-one");
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst("X-Authenticated-Roles")).isEqualTo("OPERATOR");
    }

    private ServerWebExchange withPrincipal(
            ServerWebExchange exchange,
            Principal principal) {
        return new ServerWebExchangeDecorator(exchange) {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends Principal> Mono<T> getPrincipal() {
                return Mono.just((T) principal);
            }
        };
    }
}
