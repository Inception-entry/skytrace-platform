package com.skytrace.backend.temporal.activity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionRunTimeoutsTest {

    @Test
    void usesRemainingPlanWhenItFitsTheWindow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 8, 0);
        long millis = InspectionRunTimeouts.millis(now.plusHours(2), now);
        assertThat(millis).isEqualTo(2 * 60 * 60 * 1000L);
    }

    @Test
    void keepsADayWhenThePlanIsMissingOrAlreadyOver() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 16, 8, 0);
        assertThat(InspectionRunTimeouts.millis(null, now))
                .isEqualTo(InspectionRunTimeouts.MAX.toMillis());
        assertThat(InspectionRunTimeouts.millis(now.minusMinutes(5), now))
                .isEqualTo(InspectionRunTimeouts.MAX.toMillis());
    }
}
