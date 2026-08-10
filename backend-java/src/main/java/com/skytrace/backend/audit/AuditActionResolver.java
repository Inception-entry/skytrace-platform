package com.skytrace.backend.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AuditActionResolver {

    private static final Pattern TASK =
            Pattern.compile("^/inspection-tasks(?:/([^/]+))?$");
    private static final Pattern WORKFLOW = Pattern.compile(
            "^/inspection-workflows/([^/]+)(?:/(complete|cancel|analysis)(?:/stream)?)?$"
    );
    private static final Pattern KNOWLEDGE_DELETE =
            Pattern.compile("^/knowledge/documents/([^/]+)$");
    private static final Pattern EVIDENCE_CODE = Pattern.compile(
            "^/evidence/([^/]+)(?:/(preview-url|download-url|restore))?$"
    );

    public boolean shouldAudit(HttpServletRequest request) {
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method)
                || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method)) {
            return false;
        }
        return !"/knowledge/search".equals(apiPath(request));
    }

    public AuditDescriptor resolve(HttpServletRequest request) {
        String path = apiPath(request);
        String method = request.getMethod();

        Matcher task = TASK.matcher(path);
        if (task.matches()) {
            return new AuditDescriptor(
                    HttpMethod.POST.matches(method)
                            ? "TASK_CREATE"
                            : "TASK_UPDATE",
                    "INSPECTION_TASK",
                    task.group(1)
            );
        }

        Matcher workflow = WORKFLOW.matcher(path);
        if (workflow.matches()) {
            String operation = workflow.group(2);
            String action = switch (operation == null ? "" : operation) {
                case "complete" -> "WORKFLOW_COMPLETE";
                case "cancel" -> "WORKFLOW_CANCEL";
                case "analysis" -> path.endsWith("/stream")
                        ? "AI_ANALYSIS_STREAM"
                        : "AI_ANALYSIS";
                default -> "WORKFLOW_START";
            };
            return new AuditDescriptor(
                    action,
                    "INSPECTION_TASK",
                    workflow.group(1)
            );
        }

        if ("/knowledge/documents".equals(path)) {
            return new AuditDescriptor(
                    "KNOWLEDGE_UPLOAD",
                    "KNOWLEDGE_DOCUMENT",
                    null
            );
        }

        Matcher knowledgeDelete = KNOWLEDGE_DELETE.matcher(path);
        if (knowledgeDelete.matches()) {
            return new AuditDescriptor(
                    "KNOWLEDGE_DELETE",
                    "KNOWLEDGE_DOCUMENT",
                    knowledgeDelete.group(1)
            );
        }

        if ("/alarms".equals(path)) {
            return new AuditDescriptor(
                    "ALARM_CREATE",
                    "ALARM",
                    null
            );
        }

        if ("/evidence".equals(path) && HttpMethod.POST.matches(method)) {
            return new AuditDescriptor(
                    "EVIDENCE_UPLOAD",
                    "EVIDENCE",
                    null
            );
        }

        Matcher evidence = EVIDENCE_CODE.matcher(path);
        if (evidence.matches()) {
            String operation = evidence.group(2);
            String action = switch (operation == null ? "" : operation) {
                case "preview-url" -> "EVIDENCE_PREVIEW_URL";
                case "download-url" -> "EVIDENCE_DOWNLOAD_URL";
                case "restore" -> "EVIDENCE_RESTORE";
                default -> HttpMethod.DELETE.matches(method)
                        ? "EVIDENCE_DELETE"
                        : "EVIDENCE_MUTATION";
            };
            return new AuditDescriptor(
                    action,
                    "EVIDENCE",
                    evidence.group(1)
            );
        }

        return new AuditDescriptor(
                "API_MUTATION",
                "API",
                null
        );
    }

    public String apiPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty()
                ? uri
                : uri.substring(contextPath.length());
    }

    public record AuditDescriptor(
            String action,
            String resourceType,
            String resourceId
    ) {
    }
}
