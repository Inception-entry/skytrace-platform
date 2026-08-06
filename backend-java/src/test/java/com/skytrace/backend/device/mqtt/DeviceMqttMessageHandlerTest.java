package com.skytrace.backend.device.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.cache.DevicePresenceService;
import com.skytrace.backend.device.repository.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceMqttMessageHandlerTest {

    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<DevicePresenceService> presenceProvider =
            mock(ObjectProvider.class);
    private final DevicePresenceService presence = mock(DevicePresenceService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DeviceMqttMessageHandler handler;

    @BeforeEach
    void setUp() {
        when(presenceProvider.getIfAvailable()).thenReturn(presence);
        handler = new DeviceMqttMessageHandler(
                deviceRepository,
                presenceProvider,
                objectMapper
        );
    }

    @Test
    void heartbeatForKnownDeviceTouchesPresence() {
        when(deviceRepository.existsByDeviceCode("UAV-001")).thenReturn(true);

        handler.onMessage(
                "skytrace/local/device/UAV-001/heartbeat",
                "{\"deviceCode\":\"UAV-001\",\"source\":\"sim\"}"
        );

        verify(presence).heartbeat("UAV-001");
    }

    @Test
    void unknownDeviceIsIgnored() {
        when(deviceRepository.existsByDeviceCode("GONE")).thenReturn(false);

        handler.onMessage(
                "skytrace/local/device/GONE/heartbeat",
                "{\"deviceCode\":\"GONE\",\"source\":\"sim\"}"
        );

        verifyNoInteractions(presence);
    }

    @Test
    void statusOfflineClearsPresence() {
        when(deviceRepository.existsByDeviceCode("UAV-001")).thenReturn(true);

        handler.onMessage(
                "skytrace/local/device/UAV-001/status",
                "{\"deviceCode\":\"UAV-001\",\"online\":false,\"mode\":\"OFFLINE\"}"
        );

        verify(presence).clear("UAV-001");
        verify(presence, never()).heartbeat("UAV-001");
    }
}
