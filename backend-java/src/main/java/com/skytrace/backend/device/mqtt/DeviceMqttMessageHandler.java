package com.skytrace.backend.device.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.cache.DevicePresenceService;
import com.skytrace.backend.cache.DeviceTelemetryService;
import com.skytrace.backend.device.repository.DeviceRepository;
import com.skytrace.backend.messaging.DeviceTelemetryEvent;
import com.skytrace.backend.messaging.DeviceTelemetryPublisher;
import com.skytrace.backend.telemetry.service.DeviceTelemetryHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class DeviceMqttMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(
            DeviceMqttMessageHandler.class
    );

    private final DeviceRepository deviceRepository;
    private final ObjectProvider<DevicePresenceService> presenceProvider;
    private final ObjectProvider<DeviceTelemetryService> telemetryProvider;
    private final ObjectProvider<DeviceTelemetryPublisher> telemetryPublisherProvider;
    private final DeviceTelemetryHistoryService telemetryHistoryService;
    private final ObjectMapper objectMapper;

    public DeviceMqttMessageHandler(
            DeviceRepository deviceRepository,
            ObjectProvider<DevicePresenceService> presenceProvider,
            ObjectProvider<DeviceTelemetryService> telemetryProvider,
            ObjectProvider<DeviceTelemetryPublisher> telemetryPublisherProvider,
            DeviceTelemetryHistoryService telemetryHistoryService,
            ObjectMapper objectMapper) {
        this.deviceRepository = deviceRepository;
        this.presenceProvider = presenceProvider;
        this.telemetryProvider = telemetryProvider;
        this.telemetryPublisherProvider = telemetryPublisherProvider;
        this.telemetryHistoryService = telemetryHistoryService;
        this.objectMapper = objectMapper;
    }

    public void onMessage(String topic, String payloadJson) {
        try {
            String[] parts = topic.split("/", -1);
            if (parts.length != 5
                    || !"skytrace".equals(parts[0])
                    || !"device".equals(parts[2])) {
                log.warn("忽略非法 MQTT topic: {}", topic);
                return;
            }

            String deviceCode = parts[3];
            String kind = parts[4];
            if (!"heartbeat".equals(kind)
                    && !"status".equals(kind)
                    && !"telemetry".equals(kind)) {
                return;
            }

            JsonNode payload = objectMapper.readTree(payloadJson);
            if (!deviceCode.equals(payload.path("deviceCode").asText())) {
                log.warn("MQTT topic 与 payload 的 deviceCode 不一致: {}", topic);
                return;
            }

            if (!deviceRepository.existsByDeviceCode(deviceCode)) {
                log.warn("忽略未知设备的 MQTT 消息: {}", deviceCode);
                return;
            }

            DevicePresenceService presence = presenceProvider.getIfAvailable();
            if (presence == null) {
                log.warn("Presence 未启用，忽略 MQTT 消息: {}", deviceCode);
                return;
            }

            if ("heartbeat".equals(kind)) {
                presence.heartbeat(deviceCode);
                return;
            }

            if ("telemetry".equals(kind)) {
                handleTelemetry(deviceCode, payload, presence);
                return;
            }

            JsonNode online = payload.get("online");
            if (online == null || !online.isBoolean()) {
                log.warn("status 消息缺少 boolean online: {}", deviceCode);
                return;
            }
            if (online.booleanValue()) {
                presence.heartbeat(deviceCode);
            } else {
                presence.clear(deviceCode);
                DeviceTelemetryService telemetry = telemetryProvider.getIfAvailable();
                if (telemetry != null) {
                    telemetry.clear(deviceCode);
                }
            }
        } catch (Exception error) {
            // MQTT 回调线程不能因为一条坏消息退出。
            log.warn("处理 MQTT 设备消息失败: {}", error.getMessage());
        }
    }

    private void handleTelemetry(
            String deviceCode,
            JsonNode payload,
            DevicePresenceService presence) {
        JsonNode latNode = payload.get("latitude");
        JsonNode lonNode = payload.get("longitude");
        if (latNode == null || lonNode == null
                || !latNode.isNumber() || !lonNode.isNumber()) {
            log.warn("telemetry 消息缺少 latitude/longitude: {}", deviceCode);
            return;
        }

        double latitude = latNode.asDouble();
        double longitude = lonNode.asDouble();
        Double altitude = readOptionalDouble(payload.get("altitude"));
        Double heading = readOptionalDouble(payload.get("heading"));
        String ts = payload.path("ts").asText(null);
        String source = payload.path("source").asText("mqtt");

        presence.heartbeat(deviceCode);

        DeviceTelemetryService telemetry = telemetryProvider.getIfAvailable();
        if (telemetry != null) {
            telemetry.saveLatest(deviceCode, latitude, longitude, altitude, heading, ts);
        }

        DeviceTelemetryPublisher publisher = telemetryPublisherProvider.getIfAvailable();
        if (publisher != null) {
            publisher.publish(DeviceTelemetryEvent.of(
                    deviceCode,
                    ts,
                    source,
                    latitude,
                    longitude,
                    altitude,
                    heading
            ));
        }

        telemetryHistoryService.recordIfTaskRunning(
                deviceCode, latitude, longitude, altitude, heading, source, ts);
    }

    private static Double readOptionalDouble(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asDouble();
    }
}
