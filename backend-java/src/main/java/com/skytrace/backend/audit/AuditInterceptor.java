package com.skytrace.backend.audit;

import com.skytrace.backend.audit.domain.AuditLog;
import com.skytrace.backend.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(AuditInterceptor.class);
    private static final String CONTEXT_ATTRIBUTE =
            AuditInterceptor.class.getName() + ".context";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    private final AuditActionResolver actionResolver;
    private final AuditLogService auditLogService;

    public AuditInterceptor(
            AuditActionResolver actionResolver,
            AuditLogService auditLogService) {
        this.actionResolver = actionResolver;
        this.auditLogService = auditLogService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        if (!actionResolver.shouldAudit(request)
                || request.getAttribute(CONTEXT_ATTRIBUTE) != null) {
            return true;
        }

        String requestId = requestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();
        AuditActionResolver.AuditDescriptor descriptor =
                actionResolver.resolve(request);
        AuditContext context = new AuditContext(
                requestId,
                actorId(authentication),
                username(authentication),
                roles(authentication),
                descriptor,
                request.getMethod(),
                actionResolver.apiPath(request),
                clientIp(request),
                System.nanoTime()
        );
        request.setAttribute(CONTEXT_ATTRIBUTE, context);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception) {
        Object attribute = request.getAttribute(CONTEXT_ATTRIBUTE);
        if (!(attribute instanceof AuditContext context)) {
            return;
        }
        request.removeAttribute(CONTEXT_ATTRIBUTE);

        int status = response.getStatus();
        long durationMs = Math.max(
                (System.nanoTime() - context.startedAt()) / 1_000_000,
                0
        );
        String outcome = status >= 200 && status < 400
                ? "SUCCESS"
                : "FAILURE";
        try {
            auditLogService.record(new AuditLog(
                    context.requestId(),
                    context.actorId(),
                    context.username(),
                    context.roles(),
                    context.descriptor().action(),
                    context.descriptor().resourceType(),
                    safe(context.descriptor().resourceId(), 128),
                    context.method(),
                    safe(context.path(), 512),
                    status,
                    outcome,
                    context.clientIp(),
                    durationMs,
                    exception == null
                            ? null
                            : exception.getClass().getSimpleName()
            ));
            log.info(
                    "event=audit_recorded requestId={} actorId={} action={}"
                            + " resourceType={} resourceId={} outcome={}"
                            + " status={} durationMs={}",
                    context.requestId(),
                    context.actorId(),
                    context.descriptor().action(),
                    context.descriptor().resourceType(),
                    context.descriptor().resourceId(),
                    outcome,
                    status,
                    durationMs
            );
        } catch (RuntimeException auditException) {
            log.error(
                    "event=audit_record_failed requestId={} action={}"
                            + " exceptionType={}",
                    context.requestId(),
                    context.descriptor().action(),
                    auditException.getClass().getSimpleName()
            );
        }
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
                        .filter(authority ->
                                authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring(5))
                        .sorted()
                        .collect(Collectors.joining(",")),
                256
        );
    }

    private String requestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        return incoming != null
                && SAFE_REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }

    private String clientIp(HttpServletRequest request) {
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

    private record AuditContext(
            String requestId,
            String actorId,
            String username,
            String roles,
            AuditActionResolver.AuditDescriptor descriptor,
            String method,
            String path,
            String clientIp,
            long startedAt
    ) {
    }
}
