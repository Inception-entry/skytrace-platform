package com.uav.backend.task.service;

import com.uav.backend.cache.DevicePresenceService;
import com.uav.backend.common.ConflictException;
import com.uav.backend.device.domain.Device;
import com.uav.backend.device.repository.DeviceRepository;
import com.uav.backend.task.domain.InspectionTask;
import com.uav.backend.task.dto.CreateInspectionTaskRequest;
import com.uav.backend.task.dto.InspectionTaskResponse;
import com.uav.backend.task.dto.UpdateInspectionTaskRequest;
import com.uav.backend.task.repository.InspectionTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionTaskServiceTest {

    private final InspectionTaskRepository repository =
            mock(InspectionTaskRepository.class);
    private final DeviceRepository deviceRepository =
            mock(DeviceRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<DevicePresenceService> presenceProvider =
            mock(ObjectProvider.class);
    private final DevicePresenceService presence =
            mock(DevicePresenceService.class);
    private InspectionTaskService service;

    @BeforeEach
    void setUp() {
        when(presenceProvider.getIfAvailable()).thenReturn(presence);
        when(presence.onlineDeviceCodes()).thenReturn(Set.of());
        service = new InspectionTaskService(
                repository,
                deviceRepository,
                presenceProvider
        );
    }

    @Test
    void shouldCreateTaskWithCompleteBusinessData() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 18, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 18, 11, 0);
        when(deviceRepository.existsByDeviceCode("UAV-002")).thenReturn(true);
        when(deviceRepository.findByDeviceCode("UAV-002"))
                .thenReturn(Optional.of(new Device(
                        "UAV-002",
                        "二号无人机",
                        "UAV"
                )));
        when(repository.save(any(InspectionTask.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InspectionTaskResponse response = service.create(
                new CreateInspectionTaskRequest(
                        "TASK-REAL-002",
                        "北区管线巡检",
                        "UAV-002",
                        start,
                        end
                )
        );

        assertThat(response.taskCode()).isEqualTo("TASK-REAL-002");
        assertThat(response.taskName()).isEqualTo("北区管线巡检");
        assertThat(response.deviceCode()).isEqualTo("UAV-002");
        assertThat(response.deviceName()).isEqualTo("二号无人机");
        assertThat(response.deviceStatus()).isEqualTo("OFFLINE");
        assertThat(response.status()).isEqualTo("CREATED");
        assertThat(response.planStartTime()).isEqualTo(start);
        assertThat(response.planEndTime()).isEqualTo(end);
        verify(repository).save(any(InspectionTask.class));
    }

    @Test
    void shouldRejectUnknownDevice() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 18, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 18, 11, 0);
        when(deviceRepository.existsByDeviceCode("MISSING")).thenReturn(false);

        assertThatThrownBy(() -> service.create(
                new CreateInspectionTaskRequest(
                        "TASK-REAL-004",
                        "未知设备任务",
                        "MISSING",
                        start,
                        end
                )
        ))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("设备不存在");
    }

    @Test
    void shouldRejectInvalidPlanTime() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 18, 11, 0);

        assertThatThrownBy(() -> service.create(
                new CreateInspectionTaskRequest(
                        "TASK-REAL-003",
                        "无效时间任务",
                        "UAV-003",
                        start,
                        start
                )
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("计划结束时间必须晚于计划开始时间");
    }

    @Test
    void shouldRejectEditingCompletedTask() {
        InspectionTask task = new InspectionTask(
                "TASK-DONE-001",
                "已完成任务",
                "UAV-001",
                LocalDateTime.of(2026, 7, 17, 9, 0),
                LocalDateTime.of(2026, 7, 17, 10, 0)
        );
        task.changeStatus("COMPLETED");
        when(deviceRepository.existsByDeviceCode("UAV-002")).thenReturn(true);
        when(repository.findByTaskCode("TASK-DONE-001"))
                .thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.update(
                "TASK-DONE-001",
                new UpdateInspectionTaskRequest(
                        "修改后的名称",
                        "UAV-002",
                        LocalDateTime.of(2026, 7, 18, 9, 0),
                        LocalDateTime.of(2026, 7, 18, 10, 0)
                )
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("不能修改");
    }
}
