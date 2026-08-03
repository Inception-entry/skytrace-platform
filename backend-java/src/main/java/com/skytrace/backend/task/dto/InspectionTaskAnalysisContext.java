package com.skytrace.backend.task.dto;

import java.time.LocalDateTime;

/**
 * 传给 AI 的巡检任务只读上下文。
 *
 * <p>该对象由 Java 从 MySQL 查询并组装，避免 AI 服务直接访问业务数据库。</p>
 */
public record InspectionTaskAnalysisContext(
        String taskCode,
        String taskName,
        String deviceCode,
        String status,
        LocalDateTime planStartTime,
        LocalDateTime planEndTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
