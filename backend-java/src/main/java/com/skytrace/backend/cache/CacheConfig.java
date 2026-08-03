package com.skytrace.backend.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
public class CacheConfig {
}
