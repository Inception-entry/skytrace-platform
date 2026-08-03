package com.skytrace.backend.security;

import com.skytrace.backend.audit.AdminController;
import com.skytrace.backend.audit.AuditInterceptor;
import com.skytrace.backend.audit.dto.AdminOverviewResponse;
import com.skytrace.backend.audit.service.AuditLogService;
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

@WebMvcTest(AdminController.class)
@Import({
        ApiSecurityConfig.class,
        ApiSecurityErrorHandler.class
})
@TestPropertySource(properties = {
        "app.security.enabled=true",
        "app.security.jwk-set-uri=https://issuer.example/jwks",
        "app.security.issuer-uri=https://issuer.example",
        "app.security.audience=skytrace-web"
})
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private AuditInterceptor auditInterceptor;

    @BeforeEach
    void allowMockAuditInterceptorToContinue() {
        when(auditInterceptor.preHandle(any(), any(), any()))
                .thenReturn(true);
    }

    @Test
    void administratorCanReadAdminOverview() throws Exception {
        when(auditLogService.overview()).thenReturn(
                new AdminOverviewResponse(
                        10,
                        2,
                        3,
                        4,
                        1,
                        6,
                        20,
                        0
                )
        );

        mockMvc.perform(get("/admin/overview").with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalTasks").value(10));
    }

    @Test
    void operatorCannotReadAdminOverview() throws Exception {
        mockMvc.perform(get("/admin/overview").with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_OPERATOR")
                )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void anonymousUserCannotReadAdminOverview() throws Exception {
        mockMvc.perform(get("/admin/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
