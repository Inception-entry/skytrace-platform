package com.skytrace.backend.evidence.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EvidenceActorContextService {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    public EvidenceActorContext current() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();
        HttpServletRequest request = currentRequest();
        return new EvidenceActorContext(
                actorId(authentication),
                username(authentication),
                roles(authentication),
                requestId(request),
                clientIp(request)
        );
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        return null;
    }

    private String actorId(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof Jwt jwt) {
            return safe(jwt.getSubject(), 128);
        }
        return "anonymous";
    }

    private String username(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            return safe(
                    username == null ? jwt.getSubject() : username,
                    128
            );
        }
        return "anonymous";
    }

    private String roles(Authentication authentication) {
        if (authentication == null) {
            return "";
        }
        return safe(
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring(5))
                        .sorted()
                        .collect(Collectors.joining(",")),
                256
        );
    }

    private String requestId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        return incoming != null
                && SAFE_REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String value = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
        return safe(value, 64);
    }

    private String safe(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = value
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        return sanitized.length() > maxLength
                ? sanitized.substring(0, maxLength)
                : sanitized;
    }
}