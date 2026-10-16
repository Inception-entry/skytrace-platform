package com.skytrace.gateway.security;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 未知 kid 在冷却期内不再触发 JWKS 刷新。
 */
public final class CooldownReactiveJwtDecoder implements ReactiveJwtDecoder {

    static final int MAX_KID_LENGTH = 128;
    private static final int MAX_UNKNOWN = 256;

    private final ReactiveJwtDecoder delegate;
    private final long cooldownMs;
    private final long unknownTtlMs;
    private final Set<String> knownKids = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> unknownUntil = new ConcurrentHashMap<>();
    private final AtomicLong nextRefreshAt = new AtomicLong();
    private final Object refreshLock = new Object();

    public CooldownReactiveJwtDecoder(
            ReactiveJwtDecoder delegate,
            long cooldownMs,
            long unknownTtlMs) {
        this.delegate = delegate;
        this.cooldownMs = Math.max(1L, cooldownMs);
        this.unknownTtlMs = Math.max(1L, unknownTtlMs);
    }

    @Override
    public Mono<Jwt> decode(String token) {
        final String kid;
        try {
            kid = headerKid(token);
        } catch (JwtException exception) {
            return Mono.error(exception);
        }
        long now = System.currentTimeMillis();
        if (!knownKids.contains(kid)) {
            try {
                rejectIfCoolingDown(kid, now);
            } catch (JwtException exception) {
                return Mono.error(exception);
            }
            synchronized (refreshLock) {
                now = System.currentTimeMillis();
                if (!knownKids.contains(kid)) {
                    try {
                        rejectIfCoolingDown(kid, now);
                    } catch (JwtException exception) {
                        return Mono.error(exception);
                    }
                    nextRefreshAt.set(now + cooldownMs);
                }
            }
        }
        return delegate.decode(token)
                .doOnSuccess(jwt -> {
                    knownKids.add(kid);
                    unknownUntil.remove(kid);
                })
                .doOnError(JwtException.class, exception -> rememberUnknown(kid));
    }

    private void rejectIfCoolingDown(String kid, long now) {
        Long until = unknownUntil.get(kid);
        if (until != null && until > now) {
            throw unknownKid();
        }
        if (now < nextRefreshAt.get()) {
            rememberUnknown(kid);
            throw unknownKid();
        }
    }

    private void rememberUnknown(String kid) {
        long now = System.currentTimeMillis();
        unknownUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (unknownUntil.size() >= MAX_UNKNOWN) {
            unknownUntil.clear();
        }
        unknownUntil.put(kid, now + unknownTtlMs);
    }

    private static String headerKid(String token) {
        try {
            if (!(JWTParser.parse(token) instanceof SignedJWT signed)) {
                throw unknownKid();
            }
            String kid = signed.getHeader().getKeyID();
            if (kid == null || kid.isBlank() || kid.length() > MAX_KID_LENGTH) {
                throw unknownKid();
            }
            return kid;
        } catch (ParseException exception) {
            throw unknownKid();
        }
    }

    private static JwtException unknownKid() {
        return new JwtValidationException(
                "找不到访问令牌对应的签名公钥",
                List.of(new OAuth2Error("invalid_token", "找不到访问令牌对应的签名公钥", null))
        );
    }
}
