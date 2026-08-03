package com.skytrace.backend.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.alarm.dto.AlarmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
public class AlarmRecentCache {
    private static final Logger log = LoggerFactory.getLogger(AlarmRecentCache.class);
    private static final String KEY = "skytrace:alarms:latest";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProperties properties;

    public AlarmRecentCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            CacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<List<AlarmResponse>> get() {
        try {
            String payload = redisTemplate.opsForValue().get(KEY);
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(
                    payload,
                    new TypeReference<>() {
                    }
            ));
        } catch (Exception ex) {
            log.warn("读取最近告警缓存失败: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(List<AlarmResponse> alarms) {
        if (alarms == null || alarms.isEmpty()) {
            // Never cache an empty snapshot: a concurrent create+evict can
            // otherwise be overwritten by a stale empty PUT for the full TTL.
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    KEY,
                    objectMapper.writeValueAsString(alarms),
                    Duration.ofSeconds(Math.max(1, properties.getAlarmTtlSeconds()))
            );
        } catch (JsonProcessingException ex) {
            log.warn("写入最近告警缓存失败: {}", ex.getMessage());
        } catch (Exception ex) {
            log.warn("写入最近告警缓存失败: {}", ex.getMessage());
        }
    }

    public void evict() {
        try {
            redisTemplate.delete(KEY);
        } catch (Exception ex) {
            log.warn("清理最近告警缓存失败: {}", ex.getMessage());
        }
    }
}
