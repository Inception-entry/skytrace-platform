package com.skytrace.backend.device.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.cache.DevicePresenceService;
import com.skytrace.backend.device.repository.DeviceRepository;
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
    private final ObjectMapper objectMapper;

    public DeviceMqttMessageHandler(
            DeviceRepository deviceRepository,
            ObjectProvider<DevicePresenceService> presenceProvider,
            ObjectMapper objectMapper) {
        this.deviceRepository = deviceRepository;
        this.presenceProvider = presenceProvider;
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
            if (!"heartbeat".equals(kind) && !"status".equals(kind)) {
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

            JsonNode online = payload.get("online");
            if (online == null || !online.isBoolean()) {
                log.warn("status 消息缺少 boolean online: {}", deviceCode);
                return;
            }
            if (online.booleanValue()) {
                presence.heartbeat(deviceCode);
            } else {
                presence.clear(deviceCode);
            }
        } catch (Exception error) {
            // MQTT 回调线程不能因为一条坏消息退出。
            log.warn("处理 MQTT 设备消息失败: {}", error.getMessage());
        }
    }
}
