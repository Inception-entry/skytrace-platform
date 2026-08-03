package com.skytrace.backend.audit;

import com.skytrace.backend.audit.dto.AdminOverviewResponse;
import com.skytrace.backend.audit.dto.AuditLogPageResponse;
import com.skytrace.backend.audit.service.AuditLogService;
import com.skytrace.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AuditLogService auditLogService;

    public AdminController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> overview() {
        return ApiResponse.ok(auditLogService.overview());
    }

    @GetMapping("/audit-logs")
    public ApiResponse<AuditLogPageResponse> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String username) {
        return ApiResponse.ok(auditLogService.search(
                page,
                size,
                action,
                outcome,
                username
        ));
    }
}
