package com.skytrace.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "gateway.security.enabled=true",
                "gateway.security.jwk-set-uri=https://issuer.example/jwks",
                "gateway.security.issuer-uri=https://issuer.example",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=1"
        }
)
@AutoConfigureWebTestClient
class GatewaySecurityIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void returnsStructuredUnauthorizedResponseWithRequestId() {
        webTestClient.get()
                .uri("/api/protected")
                .header("X-Request-Id", "gateway-security-test")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                )
                .expectHeader().valueEquals(
                        "X-Request-Id",
                        "gateway-security-test"
                )
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.requestId")
                .isEqualTo("gateway-security-test");
    }

    @Test
    void viewerCannotMutateInspectionTask() {
        webTestClient.mutateWith(mockJwt().authorities(
                        new SimpleGrantedAuthority("ROLE_VIEWER")
                ))
                .post()
                .uri("/api/inspection-tasks")
                .header("X-Request-Id", "gateway-viewer-write-test")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.requestId")
                .isEqualTo("gateway-viewer-write-test");
    }

    @Test
    void viewerCannotUploadKnowledgeDocument() {
        webTestClient.mutateWith(mockJwt().authorities(
                        new SimpleGrantedAuthority("ROLE_VIEWER")
                ))
                .post()
                .uri("/api/knowledge/documents")
                .header("X-Request-Id", "gateway-knowledge-role-test")
                .bodyValue("document")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.requestId")
                .isEqualTo("gateway-knowledge-role-test");
    }

    @Test
    void authenticatedUserNeedsBusinessRoleToReadApi() {
        webTestClient.mutateWith(mockJwt())
                .get()
                .uri("/api/inspection-tasks")
                .header("X-Request-Id", "gateway-missing-role-test")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void nonAdminCannotReadProtectedActuatorEndpoint() {
        webTestClient.mutateWith(mockJwt().authorities(
                        new SimpleGrantedAuthority("ROLE_OPERATOR")
                ))
                .get()
                .uri("/actuator/metrics")
                .header("X-Request-Id", "gateway-actuator-role-test")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void operatorCannotReadAdminApi() {
        webTestClient.mutateWith(mockJwt().authorities(
                        new SimpleGrantedAuthority("ROLE_OPERATOR")
                ))
                .get()
                .uri("/api/admin/audit-logs")
                .header("X-Request-Id", "gateway-admin-role-test")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.requestId")
                .isEqualTo("gateway-admin-role-test");
    }
}
