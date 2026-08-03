package com.skytrace.backend.device.service;

import com.skytrace.backend.cache.DevicePresenceService;
import com.skytrace.backend.common.ConflictException;
import com.skytrace.backend.device.domain.Device;
import com.skytrace.backend.device.dto.CreateDeviceRequest;
import com.skytrace.backend.device.dto.DeviceResponse;
import com.skytrace.backend.device.dto.UpdateDeviceRequest;
import com.skytrace.backend.device.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceServiceTest {

    private final DeviceRepository repository =
            mock(DeviceRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<DevicePresenceService> presenceProvider =
            mock(ObjectProvider.class);
    private final DevicePresenceService presence =
            mock(DevicePresenceService.class);
    private DeviceService service;

    @BeforeEach
    void setUp() {
        when(presenceProvider.getIfAvailable()).thenReturn(presence);
        service = new DeviceService(repository, presenceProvider);
    }

    @Test
    void shouldListDevicesWithRedisPresenceOverlay() {
        Device offline = new Device(
                "CAMERA-001",
                "一号固定摄像头",
                "CAMERA"
        );
        Device online = new Device(
                "UAV-001",
                "一号无人机",
                "UAV"
        );
        when(repository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(offline, online));
        when(presence.onlineDeviceCodes()).thenReturn(Set.of("UAV-001"));

        List<DeviceResponse> devices = service.findAll();

        assertThat(devices).hasSize(2);
        assertThat(devices.get(0).deviceCode()).isEqualTo("CAMERA-001");
        assertThat(devices.get(0).status()).isEqualTo("OFFLINE");
        assertThat(devices.get(1).deviceCode()).isEqualTo("UAV-001");
        assertThat(devices.get(1).status()).isEqualTo("ONLINE");
    }

    @Test
    void shouldCreateDevice() {
        when(repository.existsByDeviceCode("UAV-002")).thenReturn(false);
        when(repository.save(any(Device.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(presence.onlineDeviceCodes()).thenReturn(Set.of());

        DeviceResponse response = service.create(
                new CreateDeviceRequest(
                        "UAV-002",
                        "二号无人机",
                        "uav"
                )
        );

        assertThat(response.deviceCode()).isEqualTo("UAV-002");
        assertThat(response.deviceName()).isEqualTo("二号无人机");
        assertThat(response.deviceType()).isEqualTo("UAV");
        assertThat(response.status()).isEqualTo("OFFLINE");
        verify(repository).save(any(Device.class));
    }

    @Test
    void shouldRejectDuplicateDeviceCode() {
        when(repository.existsByDeviceCode("UAV-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateDeviceRequest(
                        "UAV-001",
                        "重复设备",
                        "UAV"
                )
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void shouldUpdateDevice() {
        Device device = new Device(
                "UAV-001",
                "一号无人机",
                "UAV"
        );
        when(repository.findByDeviceCode("UAV-001"))
                .thenReturn(Optional.of(device));
        when(presence.onlineDeviceCodes()).thenReturn(Set.of("UAV-001"));

        DeviceResponse response = service.update(
                "UAV-001",
                new UpdateDeviceRequest("北区无人机", "UAV")
        );

        assertThat(response.deviceName()).isEqualTo("北区无人机");
        assertThat(response.status()).isEqualTo("ONLINE");
    }

    @Test
    void shouldRejectHeartbeatForUnknownDevice() {
        when(repository.findByDeviceCode("MISSING"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.heartbeat("MISSING"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("设备不存在");
    }

    @Test
    void shouldHeartbeatKnownDevice() {
        Device device = new Device(
                "UAV-001",
                "一号无人机",
                "UAV"
        );
        when(repository.findByDeviceCode("UAV-001"))
                .thenReturn(Optional.of(device));

        Map<String, Object> result = service.heartbeat("UAV-001");

        assertThat(result.get("status")).isEqualTo("ONLINE");
        assertThat(result.get("presence")).isEqualTo("ok");
        verify(presence).heartbeat("UAV-001");
    }
}
