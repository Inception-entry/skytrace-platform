package com.skytrace.backend.evidence;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(EvidenceMaintenanceProperties.class)
public class EvidenceMaintenanceConfig {
    // 这个配置类只开启属性绑定和 Spring 调度，实际任务仍受 enabled 配置保护。
}
