package com.skytrace.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GatewaySecurityErrorHandler
        implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final ObjectMapper objectMapper;

    public GatewaySecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException exception) {
        exchange.getResponse().getHeaders().set(
                HttpHeaders.WWW_AUTHENTICATE,
                "Bearer"
        );
        return write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "未登录或访问令牌无效"
        );
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            org.springframework.security.access.AccessDeniedException exception) {
        return write(
                exchange,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "当前用户没有访问该资源的权限"
        );
    }

    private Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String code,
            String message) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(
                MediaType.APPLICATION_JSON
        );
        exchange.getResponse().getHeaders().setCacheControl("no-store");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("code", code);
        payload.put("message", message);
        payload.put("requestId", resolveRequestId(exchange));
        payload.put("timestamp", Instant.now().toString());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            bytes = ("{\"success\":false,\"code\":\"" + code
                    + "\",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse()
                .bufferFactory()
                .wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String resolveRequestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest()
                .getHeaders()
                .getFirst(REQUEST_ID_HEADER);
        return requestId == null ? "unknown" : requestId;
    }
}
