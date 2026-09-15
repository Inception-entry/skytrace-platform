package com.skytrace.backend.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseTimesTest {

    @Test
    void convertsUtcZToShanghaiWallClock() {
        assertThat(DatabaseTimes.parseJsonLocalDateTime("2026-08-24T02:00:00Z"))
                .isEqualTo(LocalDateTime.of(2026, 8, 24, 10, 0));
    }

    @Test
    void convertsPlusEightOffsetToSameShanghaiWallClock() {
        assertThat(DatabaseTimes.parseJsonLocalDateTime("2026-08-24T10:00:00+08:00"))
                .isEqualTo(LocalDateTime.of(2026, 8, 24, 10, 0));
    }

    @Test
    void crossesUtcDayBoundaryIntoShanghaiNextDay() {
        assertThat(DatabaseTimes.parseJsonLocalDateTime("2026-08-24T16:30:00Z"))
                .isEqualTo(LocalDateTime.of(2026, 8, 25, 0, 30));
    }

    @Test
    void keepsNaiveDatetimeAsShanghaiWallClock() {
        assertThat(DatabaseTimes.parseJsonLocalDateTime("2026-08-24T10:00:00"))
                .isEqualTo(LocalDateTime.of(2026, 8, 24, 10, 0));
    }

    @Test
    void rejectsUnparseableValues() {
        assertThatThrownBy(() -> DatabaseTimes.parseJsonLocalDateTime("not-a-time"))
                .isInstanceOf(java.time.format.DateTimeParseException.class);
    }
}
