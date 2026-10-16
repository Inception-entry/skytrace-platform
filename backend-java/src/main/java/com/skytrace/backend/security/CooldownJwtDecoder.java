package com.skytrace.backend.security;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 未知 kid 在冷却期内不再触发 JWKS 刷新，避免把验签端变成 JWKS 放大器。
 */
public final class CooldownJwtDecoder implements JwtDecoder {

    static final int MAX_KID_LENGTH = 128;
    private static final int MAX_UNKNOWN = 256;

    private final JwtDecoder delegate;
    private final long cooldownMs;
    private final long unknownTtlMs;
    private final Set<String> knownKids = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> unknownUntil = new ConcurrentHashMap<>();
    private final AtomicLong nextRefreshAt = new AtomicLong();
    private final Object refreshLock = new Object();

    public CooldownJwtDecoder(JwtDecoder delegate, long cooldownMs, long unknownTtlMs) {
        this.delegate = delegate;
        this.cooldownMs = Math.max(1L, cooldownMs);
        this.unknownTtlMs = Math.max(1L, unknownTtlMs);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String kid = headerKid(token);
        long now = System.currentTimeMillis();
        if (knownKids.contains(kid)) {
            return delegate.decode(token);
        }
        rejectIfCoolingDown(kid, now);
        synchronized (refreshLock) {
            now = System.currentTimeMillis();
            if (!knownKids.contains(kid)) {
                rejectIfCoolingDown(kid, now);
                nextRefreshAt.set(now + cooldownMs);
            }
        }
        try {
            Jwt jwt = delegate.decode(token);
            knownKids.add(kid);
            unknownUntil.remove(kid);
            return jwt;
        } catch (JwtException exception) {
            rememberUnknown(kid);
            throw exception;
        }
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
