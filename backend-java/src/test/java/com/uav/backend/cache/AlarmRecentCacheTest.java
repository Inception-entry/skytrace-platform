package com.uav.backend.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.backend.alarm.dto.AlarmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlarmRecentCacheTest {

    @Test
    void shouldSkipCachingEmptySnapshot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        CacheProperties properties = new CacheProperties();
        properties.setAlarmTtlSeconds(30);

        AlarmRecentCache cache = new AlarmRecentCache(
                redisTemplate,
                new ObjectMapper(),
                properties
        );
        cache.put(List.of());

        verify(values, never()).set(anyString(), anyString(), any());
    }
}
