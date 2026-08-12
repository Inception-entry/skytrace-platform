package com.skytrace.backend.telemetry.service;

import com.skytrace.backend.task.domain.InspectionTask;
import com.skytrace.backend.task.repository.InspectionTaskRepository;
import com.skytrace.backend.telemetry.domain.DeviceTelemetryPoint;
import com.skytrace.backend.telemetry.dto.DeviceTelemetryPointResponse;
import com.skytrace.backend.telemetry.repository.DeviceTelemetryPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceTelemetryHistoryServiceTest {

    private final DeviceTelemetryPointRepository telemetryRepository =
            mock(DeviceTelemetryPointRepository.class);
    private final InspectionTaskRepository taskRepository =
            mock(InspectionTaskRepository.class);
    private DeviceTelemetryHistoryService service;

    @BeforeEach
    void setUp() {
        service = new DeviceTelemetryHistoryService(
                telemetryRepository,
                taskRepository
        );
    }

    @Test
    void recordsPointWhenDeviceHasRunningTask() {
        InspectionTask running = new InspectionTask(
                "TASK-FLY-1",
                "飞行中",
                "UAV-001",
                "ROUTE-001",
                LocalDateTime.of(2026, 8, 12, 9, 0),
                LocalDateTime.of(2026, 8, 12, 10, 0)
        );
        running.changeStatus("RUNNING");
        when(taskRepository.findFirstByDeviceCodeAndStatusOrderByUpdatedAtDesc(
                "UAV-001", "RUNNING"
        )).thenReturn(Optional.of(running));
        when(telemetryRepository.save(any(DeviceTelemetryPoint.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.recordIfTaskRunning(
                "UAV-001",
                31.2304,
                121.4737,
                120.0,
                45.0,
                "sim",
                "2026-08-12T01:00:00Z"
        );

        ArgumentCaptor<DeviceTelemetryPoint> captor =
                ArgumentCaptor.forClass(DeviceTelemetryPoint.class);
        verify(telemetryRepository).save(captor.capture());
        DeviceTelemetryPoint saved = captor.getValue();
        assertThat(saved.getTaskCode()).isEqualTo("TASK-FLY-1");
        assertThat(saved.getDeviceCode()).isEqualTo("UAV-001");
        assertThat(saved.getLatitude()).isEqualByComparingTo("31.2304");
        assertThat(saved.getLongitude()).isEqualByComparingTo("121.4737");
        assertThat(saved.getAltitude()).isEqualByComparingTo("120.0");
        assertThat(saved.getHeading()).isEqualByComparingTo("45.0");
        assertThat(saved.getSource()).isEqualTo("sim");
    }

    @Test
    void skipsPersistWhenNoRunningTask() {
        when(taskRepository.findFirstByDeviceCodeAndStatusOrderByUpdatedAtDesc(
                eq("UAV-001"), eq("RUNNING")
        )).thenReturn(Optional.empty());

        service.recordIfTaskRunning(
                "UAV-001", 1.0, 2.0, null, null, "sim", null
        );

        verify(telemetryRepository, never()).save(any());
    }

    @Test
    void findTrackMapsRepositoryRows() {
        DeviceTelemetryPoint point = new DeviceTelemetryPoint(
                "UAV-001",
                "TASK-FLY-1",
                BigDecimal.valueOf(31.23),
                BigDecimal.valueOf(121.47),
                BigDecimal.valueOf(80),
                BigDecimal.valueOf(10),
                "sim",
                LocalDateTime.of(2026, 8, 12, 9, 5)
        );
        when(telemetryRepository.findByTaskCodeOrderByRecordedAtAsc("TASK-FLY-1"))
                .thenReturn(List.of(point));

        List<DeviceTelemetryPointResponse> track =
                service.findTrackByTaskCode("TASK-FLY-1");

        assertThat(track).hasSize(1);
        assertThat(track.get(0).latitude()).isEqualByComparingTo("31.23");
        assertThat(track.get(0).longitude()).isEqualByComparingTo("121.47");
        assertThat(track.get(0).recordedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 12, 9, 5));
    }
}
