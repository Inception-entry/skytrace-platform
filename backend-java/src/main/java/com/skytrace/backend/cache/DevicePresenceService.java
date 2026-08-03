package com.skytrace.backend.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
public class DevicePresenceService {
    private static final Logger log = LoggerFactory.getLogger(DevicePresenceService.class);
    private static final String KEY_PREFIX = "skytrace:device:online:";

    private final StringRedisTemplate redisTemplate;
    private final CacheProperties properties;

    public DevicePresenceService(
            StringRedisTemplate redisTemplate,
            CacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void heartbeat(String deviceCode) {
        String code = normalize(deviceCode);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("deviceCode 不能为空");
        }
        try {
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + code,
                    "1",
                    Duration.ofSeconds(Math.max(
                            15,
                            properties.getDevicePresenceTtlSeconds()
                    ))
            );
        } catch (Exception ex) {
            log.warn("写入设备在线状态失败: {}", ex.getMessage());
            throw new IllegalStateException("设备在线状态写入失败", ex);
        }
    }

    public boolean isOnline(String deviceCode) {
        String code = normalize(deviceCode);
        if (code.isEmpty()) {
            return false;
        }
        try {
            Boolean exists = redisTemplate.hasKey(KEY_PREFIX + code);
            return Boolean.TRUE.equals(exists);
        } catch (Exception ex) {
            log.warn("读取设备在线状态失败: {}", ex.getMessage());
            return false;
        }
    }

    public Set<String> onlineDeviceCodes() {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return Set.of();
            }
            Set<String> codes = new HashSet<>();
            for (String key : keys) {
                codes.add(key.substring(KEY_PREFIX.length()));
            }
            return codes;
        } catch (Exception ex) {
            log.warn("扫描在线设备失败: {}", ex.getMessage());
            return Set.of();
        }
    }

    private static String normalize(String deviceCode) {
        return deviceCode == null ? "" : deviceCode.trim();
    }
}
