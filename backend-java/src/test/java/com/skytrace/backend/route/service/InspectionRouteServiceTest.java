package com.skytrace.backend.route.service;

import com.skytrace.backend.common.ConflictException;
import com.skytrace.backend.route.domain.InspectionRoute;
import com.skytrace.backend.route.dto.CreateRouteRequest;
import com.skytrace.backend.route.dto.RouteResponse;
import com.skytrace.backend.route.repository.InspectionRouteRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionRouteServiceTest {

    private final InspectionRouteRepository repository =
            mock(InspectionRouteRepository.class);
    private final InspectionRouteService service =
            new InspectionRouteService(repository);

    @Test
    void shouldCreateRoute() {
        when(repository.existsByRouteCode("ROUTE-002")).thenReturn(false);
        when(repository.save(any(InspectionRoute.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RouteResponse response = service.create(
                new CreateRouteRequest(
                        "ROUTE-002",
                        "西区航线",
                        "演示",
                        "[{\"lat\":1,\"lng\":2}]"
                )
        );

        assertThat(response.routeCode()).isEqualTo("ROUTE-002");
        assertThat(response.routeName()).isEqualTo("西区航线");
        verify(repository).save(any(InspectionRoute.class));
    }

    @Test
    void shouldRejectDuplicateRouteCode() {
        when(repository.existsByRouteCode("ROUTE-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateRouteRequest(
                        "ROUTE-001",
                        "重复",
                        null,
                        null
                )
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void shouldRejectMissingRoute() {
        when(repository.findByRouteCode("MISSING"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByRouteCode("MISSING"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("航线不存在");
    }
}
