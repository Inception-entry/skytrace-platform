package com.skytrace.backend.device.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.cache.DevicePresenceService;
import com.skytrace.backend.cache.DeviceTelemetryService;
import com.skytrace.backend.device.repository.DeviceRepository;
import com.skytrace.backend.messaging.DeviceTelemetryEvent;
import com.skytrace.backend.messaging.DeviceTelemetryPublisher;
import com.skytrace.backend.telemetry.service.DeviceTelemetryHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @SuppressWarnings("unchecked")
    private final ObjectProvider<DeviceTelemetryService> telemetryProvider =
            mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<DeviceTelemetryPublisher> telemetryPublisherProvider =
            mock(ObjectProvider.class);
    private final DevicePresenceService presence = mock(DevicePresenceService.class);
    private final DeviceTelemetryService telemetry = mock(DeviceTelemetryService.class);
    private final DeviceTelemetryPublisher telemetryPublisher =
            mock(DeviceTelemetryPublisher.class);
    private final DeviceTelemetryHistoryService telemetryHistoryService =
            mock(DeviceTelemetryHistoryService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DeviceMqttMessageHandler handler;

    @BeforeEach
    void setUp() {
        when(presenceProvider.getIfAvailable()).thenReturn(presence);
        when(telemetryProvider.getIfAvailable()).thenReturn(telemetry);
        when(telemetryPublisherProvider.getIfAvailable()).thenReturn(telemetryPublisher);
        handler = new DeviceMqttMessageHandler(
                deviceRepository,
                presenceProvider,
                telemetryProvider,
                telemetryPublisherProvider,
                telemetryHistoryService,
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
        verifyNoInteractions(telemetry);
        verifyNoInteractions(telemetryPublisher);
        verifyNoInteractions(telemetryHistoryService);
    }

    @Test
    void statusOfflineClearsPresenceAndTelemetry() {
        when(deviceRepository.existsByDeviceCode("UAV-001")).thenReturn(true);

        handler.onMessage(
                "skytrace/local/device/UAV-001/status",
                "{\"deviceCode\":\"UAV-001\",\"online\":false,\"mode\":\"OFFLINE\"}"
        );

        verify(presence).clear("UAV-001");
        verify(presence, never()).heartbeat("UAV-001");
        verify(telemetry).clear("UAV-001");
    }

    @Test
    void telemetryForKnownDeviceWritesRedisAndPublishes() {
        when(deviceRepository.existsByDeviceCode("UAV-001")).thenReturn(true);

        handler.onMessage(
                "skytrace/local/device/UAV-001/telemetry",
                "{"
                        + "\"deviceCode\":\"UAV-001\","
                        + "\"ts\":\"2026-08-12T01:00:00Z\","
                        + "\"source\":\"sim\","
                        + "\"latitude\":31.2304,"
                        + "\"longitude\":121.4737,"
                        + "\"altitude\":120.0,"
                        + "\"heading\":45.0"
                        + "}"
        );

        verify(presence).heartbeat("UAV-001");
        verify(telemetry).saveLatest(
                eq("UAV-001"),
                eq(31.2304),
                eq(121.4737),
                eq(120.0),
                eq(45.0),
                eq("2026-08-12T01:00:00Z")
        );

        ArgumentCaptor<DeviceTelemetryEvent> captor =
                ArgumentCaptor.forClass(DeviceTelemetryEvent.class);
        verify(telemetryPublisher).publish(captor.capture());
        DeviceTelemetryEvent event = captor.getValue();
        assertEquals(DeviceTelemetryEvent.TYPE, event.type());
        assertEquals("UAV-001", event.deviceCode());
        assertEquals(31.2304, event.latitude());
        assertEquals(121.4737, event.longitude());
        assertEquals(120.0, event.altitude());
        assertEquals(45.0, event.heading());

        verify(telemetryHistoryService).recordIfTaskRunning(
                eq("UAV-001"),
                eq(31.2304),
                eq(121.4737),
                eq(120.0),
                eq(45.0),
                eq("sim"),
                eq("2026-08-12T01:00:00Z")
        );
    }

    @Test
    void telemetryMissingCoordinatesIsIgnored() {
        when(deviceRepository.existsByDeviceCode("UAV-001")).thenReturn(true);

        handler.onMessage(
                "skytrace/local/device/UAV-001/telemetry",
                "{\"deviceCode\":\"UAV-001\",\"source\":\"sim\",\"altitude\":10}"
        );

        verify(presence, never()).heartbeat("UAV-001");
        verifyNoInteractions(telemetry);
        verifyNoInteractions(telemetryPublisher);
        verifyNoInteractions(telemetryHistoryService);
    }

    @Test
    void telemetryUnknownDeviceIsIgnored() {
        when(deviceRepository.existsByDeviceCode("UAV-999")).thenReturn(false);

        handler.onMessage(
                "skytrace/local/device/UAV-999/telemetry",
                "{\"deviceCode\":\"UAV-999\",\"latitude\":1.0,\"longitude\":2.0}"
        );

        verifyNoInteractions(presence);
        verifyNoInteractions(telemetry);
        verifyNoInteractions(telemetryPublisher);
        verifyNoInteractions(telemetryHistoryService);
    }

    @Test
    void telemetryWithoutOptionalFieldsStillWorks() {
        when(deviceRepository.existsByDeviceCode("UAV-001")).thenReturn(true);

        handler.onMessage(
                "skytrace/local/device/UAV-001/telemetry",
                "{\"deviceCode\":\"UAV-001\",\"latitude\":31.0,\"longitude\":121.0}"
        );

        verify(presence).heartbeat("UAV-001");
        verify(telemetry).saveLatest(
                eq("UAV-001"),
                eq(31.0),
                eq(121.0),
                isNull(),
                isNull(),
                isNull()
        );
        verify(telemetryPublisher).publish(org.mockito.ArgumentMatchers.any());
        verify(telemetryHistoryService).recordIfTaskRunning(
                eq("UAV-001"), eq(31.0), eq(121.0), isNull(), isNull(), eq("mqtt"), isNull()
        );
    }
}
