package com.skytrace.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GatewayJavaApplicationTests {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void contextLoads() {
    }

    @Test
    void configuresApiAnalysisAndWebsocketRoutes() {
        List<RouteDefinition> routes = routeDefinitionLocator
                .getRouteDefinitions()
                .collectList()
                .block();

        assertThat(routes).isNotNull();
        assertThat(routes)
                .extracting(RouteDefinition::getId)
                .containsExactlyInAnyOrder(
                        "node-bff-analysis",
                        "node-bff-knowledge",
                        "node-bff-api",
                        "node-bff-websocket",
                        "node-bff-socket-http"
                );

        RouteDefinition analysisRoute = routes.stream()
                .filter(route -> "node-bff-analysis".equals(route.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(analysisRoute.getOrder()).isEqualTo(-10);
        assertThat(analysisRoute.getPredicates())
                .anySatisfy(predicate -> {
                    assertThat(predicate.getName()).isEqualTo("Path");
                    assertThat(predicate.getArgs().values())
                            .contains(
                                    "/api/inspection-tasks/*/analysis",
                                    "/api/inspection-tasks/*/analysis/stream"
                            );
                });
        assertThat(analysisRoute.getFilters())
                .anySatisfy(filter -> {
                    assertThat(filter.getName())
                            .isEqualTo("RequestRateLimiter");
                    assertThat(filter.getArgs())
                            .containsEntry(
                                    "redis-rate-limiter.replenishrate",
                                    "1"
                            )
                            .containsEntry(
                                    "redis-rate-limiter.burstcapacity",
                                    "2"
                            );
                });
    }
}
