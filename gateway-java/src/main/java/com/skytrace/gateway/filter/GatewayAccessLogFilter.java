package com.skytrace.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class GatewayAccessLogFilter implements WebFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(GatewayAccessLogFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("^[A-Za-z0-9._-]{8,128}$");
    private final RemoteAddressResolver remoteAddressResolver;

    public GatewayAccessLogFilter(
            RemoteAddressResolver remoteAddressResolver) {
        this.remoteAddressResolver = remoteAddressResolver;
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            WebFilterChain chain) {
        Instant startedAt = Instant.now();
        String requestId = resolveRequestId(exchange);
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(
                        REQUEST_ID_HEADER,
                        requestId
                ))
                .build();
        ServerWebExchange tracedExchange = exchange.mutate()
                .request(request)
                .build();
        tracedExchange.getResponse().getHeaders().set(
                REQUEST_ID_HEADER,
                requestId
        );

        return chain.filter(tracedExchange)
                .doOnSuccess(ignored -> logRequest(
                        tracedExchange,
                        requestId,
                        startedAt,
                        null
                ))
                .doOnError(error -> logRequest(
                        tracedExchange,
                        requestId,
                        startedAt,
                        error
                ));
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        String incoming = exchange.getRequest()
                .getHeaders()
                .getFirst(REQUEST_ID_HEADER);
        if (incoming != null && SAFE_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    private void logRequest(
            ServerWebExchange exchange,
            String requestId,
            Instant startedAt,
            Throwable error) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        long durationMs = Duration.between(
                startedAt,
                Instant.now()
        ).toMillis();
        String message = "gateway requestId={} clientIp={} route={} method={} path={} status={} durationMs={}";
        String clientIp = resolveClientIp(exchange);
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? "unmatched" : route.getId();

        if (error == null) {
            log.info(
                    message,
                    requestId,
                    clientIp,
                    routeId,
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(),
                    status == null ? 200 : status.value(),
                    durationMs
            );
        } else {
            log.warn(
                    message + " error={}",
                    requestId,
                    clientIp,
                    routeId,
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath().value(),
                    status == null ? 500 : status.value(),
                    durationMs,
                    error.getClass().getSimpleName()
            );
        }
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        try {
            InetSocketAddress resolved = remoteAddressResolver.resolve(exchange);
            if (resolved != null && resolved.getAddress() != null) {
                return resolved.getAddress().getHostAddress();
            }
            return resolved == null ? "unknown" : resolved.getHostString();
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
