package com.skytrace.backend.temporal.activity;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 按计划结束时间计算本次运行最多再等多久。没有计划或已经过点时仍给一个短窗口，避免立刻超时抖动。
 */
public final class InspectionRunTimeouts {

    public static final Duration MIN = Duration.ofMinutes(1);
    public static final Duration MAX = Duration.ofHours(24);

    private InspectionRunTimeouts() {
    }

    public static long millis(LocalDateTime planEndTime, LocalDateTime now) {
        if (planEndTime == null || now == null || !planEndTime.isAfter(now)) {
            return MAX.toMillis();
        }
        long remaining = Duration.between(now, planEndTime).toMillis();
        return Math.max(MIN.toMillis(), Math.min(MAX.toMillis(), remaining));
    }
}
