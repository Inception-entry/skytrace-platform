package com.skytrace.backend.security;

import com.skytrace.backend.audit.AuditInterceptor;
import com.skytrace.backend.evidence.EvidenceMaintenanceController;
import com.skytrace.backend.evidence.EvidenceMaintenanceProperties;
import com.skytrace.backend.evidence.service.EvidenceCleanupService;
import com.skytrace.backend.evidence.service.EvidenceHashBackfillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvidenceMaintenanceController.class)
@Import({
        ApiSecurityConfig.class,
        ApiSecurityErrorHandler.class
})
@TestPropertySource(properties = {
        "app.minio.enabled=true",
        "app.security.enabled=true",
        "app.security.jwk-set-uri=https://issuer.example/jwks",
        "app.security.issuer-uri=https://issuer.example",
        "app.security.audience=skytrace-web"
})
class EvidenceMaintenanceSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EvidenceHashBackfillService hashBackfillService;

    @MockBean
    private EvidenceCleanupService cleanupService;

    @MockBean
    private EvidenceMaintenanceProperties properties;

    @MockBean
    private AuditInterceptor auditInterceptor;

    @BeforeEach
    void setUp() {
        // MVC 切片只验证权限和响应，不在这里重复测试审计拦截器内部实现。
        when(auditInterceptor.preHandle(any(), any(), any()))
                .thenReturn(true);
        when(properties.getCleanupRetentionDays()).thenReturn(90);
        when(properties.getCleanupBatchSize()).thenReturn(20);
        when(properties.getCleanupCron()).thenReturn("0 30 3 * * *");
        when(properties.getHashBackfillBatchSize()).thenReturn(25);
        when(properties.getHashBackfillRetryHours()).thenReturn(24);
    }

    @Test
    void administratorCanReadEvidenceMaintenancePolicy() throws Exception {
        // ADMIN 可以查看当前生效的保留期和调度策略。
        mockMvc.perform(get("/admin/evidence-maintenance/policy")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cleanupRetentionDays")
                        .value(90));
    }

    @Test
    void operatorCannotAccessEvidenceMaintenancePolicy() throws Exception {
        // 普通操作员可以创建归档，但不能查看或触发物理清理运维接口。
        mockMvc.perform(get("/admin/evidence-maintenance/policy")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_OPERATOR")
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
