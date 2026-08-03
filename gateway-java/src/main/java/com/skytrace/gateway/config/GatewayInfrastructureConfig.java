package com.skytrace.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class GatewayInfrastructureConfig {

    @Bean
    public RemoteAddressResolver trustedProxyRemoteAddressResolver(
            @Value("${gateway.trusted-proxy-count:1}")
            int trustedProxyCount) {
        if (trustedProxyCount < 1) {
            throw new IllegalArgumentException(
                    "GATEWAY_TRUSTED_PROXY_COUNT 必须大于等于 1"
            );
        }
        return XForwardedRemoteAddressResolver.maxTrustedIndex(
                trustedProxyCount
        );
    }

    @Bean
    public KeyResolver gatewayKeyResolver(
            RemoteAddressResolver remoteAddressResolver) {
        return exchange -> exchange.getPrincipal()
                .filter(this::isAuthenticatedUser)
                .map(principal -> "user:" + principal.getName())
                .switchIfEmpty(Mono.fromSupplier(
                        () -> "ip:" + resolveClientIp(
                                exchange,
                                remoteAddressResolver
                        )
                ));
    }

    private boolean isAuthenticatedUser(java.security.Principal principal) {
        return principal instanceof Authentication authentication
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
    }

    private String resolveClientIp(
            ServerWebExchange exchange,
            RemoteAddressResolver remoteAddressResolver) {
        InetSocketAddress remoteAddress = remoteAddressResolver.resolve(
                exchange
        );
        if (remoteAddress == null) {
            return "unknown";
        }
        return remoteAddress.getAddress() == null
                ? remoteAddress.getHostString()
                : remoteAddress.getAddress().getHostAddress();
    }
}
