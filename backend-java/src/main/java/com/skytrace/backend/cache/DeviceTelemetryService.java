package com.skytrace.backend.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
public class DeviceTelemetryService {
    private static final Logger log = LoggerFactory.getLogger(DeviceTelemetryService.class);
    private static final String KEY_PREFIX = "skytrace:device:telemetry:";

    private final StringRedisTemplate redisTemplate;
    private final CacheProperties properties;

    public DeviceTelemetryService(
            StringRedisTemplate redisTemplate,
            CacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void saveLatest(
            String deviceCode,
            double latitude,
            double longitude,
            Double altitude,
            Double heading,
            String ts) {
        String code = normalize(deviceCode);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("deviceCode 不能为空");
        }
        try {
            String key = KEY_PREFIX + code;
            Map<String, String> values = new HashMap<>();
            values.put("latitude", Double.toString(latitude));
            values.put("longitude", Double.toString(longitude));
            if (altitude != null) {
                values.put("altitude", Double.toString(altitude));
            }
            if (heading != null) {
                values.put("heading", Double.toString(heading));
            }
            if (ts != null && !ts.isBlank()) {
                values.put("ts", ts);
            }
            redisTemplate.opsForHash().putAll(key, values);
            redisTemplate.expire(
                    key,
                    Duration.ofSeconds(Math.max(
                            15,
                            properties.getDevicePresenceTtlSeconds()
                    ))
            );
        } catch (Exception ex) {
            log.warn("写入设备遥测失败: {}", ex.getMessage());
            throw new IllegalStateException("设备遥测写入失败", ex);
        }
    }

    public void clear(String deviceCode) {
        String code = normalize(deviceCode);
        if (code.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + code);
        } catch (Exception ex) {
            log.warn("清除设备遥测失败: {}", ex.getMessage());
        }
    }

    private static String normalize(String deviceCode) {
        return deviceCode == null ? "" : deviceCode.trim();
    }
}
