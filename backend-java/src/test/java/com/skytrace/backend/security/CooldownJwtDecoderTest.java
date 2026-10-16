package com.skytrace.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CooldownJwtDecoderTest {

    @Test
    void secondUnknownKidDoesNotCallDelegateDuringCooldown() {
        AtomicInteger calls = new AtomicInteger();
        JwtDecoder delegate = token -> {
            calls.incrementAndGet();
            throw new JwtException("unknown kid");
        };
        CooldownJwtDecoder decoder = new CooldownJwtDecoder(delegate, 10_000L, 30_000L);

        assertThatThrownBy(() -> decoder.decode(unsignedToken("kid-a")))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(unsignedToken("kid-b")))
                .isInstanceOf(JwtException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void knownKidStillReachesDelegate() {
        AtomicInteger calls = new AtomicInteger();
        JwtDecoder delegate = token -> {
            calls.incrementAndGet();
            return jwt();
        };
        CooldownJwtDecoder decoder = new CooldownJwtDecoder(delegate, 10_000L, 30_000L);

        assertThat(decoder.decode(unsignedToken("kid-ok")).getSubject())
                .isEqualTo("user");
        assertThat(decoder.decode(unsignedToken("kid-ok")).getSubject())
                .isEqualTo("user");
        assertThat(calls).hasValue(2);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private static String unsignedToken(String kid) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"alg\":\"none\",\"kid\":\"" + kid + "\"}")
                        .getBytes(StandardCharsets.UTF_8)
        );
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"user\"}".getBytes(StandardCharsets.UTF_8)
        );
        return header + "." + payload + ".sig";
    }
}
