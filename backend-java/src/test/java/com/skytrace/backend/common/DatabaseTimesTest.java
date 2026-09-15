package com.skytrace.backend.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

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

    @Test
    void shanghaiAfternoonWallClockBecomesUtcMorning() {
        Instant instant = DatabaseTimes.toInstant(
                LocalDateTime.of(2026, 8, 24, 16, 0)
        );
        assertThat(instant).isEqualTo(Instant.parse("2026-08-24T08:00:00Z"));
        assertThat(instant).isNotEqualTo(
                LocalDateTime.of(2026, 8, 24, 16, 0)
                        .atZone(ZoneOffset.UTC)
                        .toInstant()
        );
    }

    @Test
    void utcAfternoonCrossesIntoShanghaiNextDay() {
        assertThat(DatabaseTimes.toDatabaseLocal(
                Instant.parse("2026-08-24T16:00:00Z")
        )).isEqualTo(LocalDateTime.of(2026, 8, 25, 0, 0));
    }

    @Test
    void searchStartInstantMapsToShanghaiWallClock() {
        assertThat(DatabaseTimes.toDatabaseLocal(
                Instant.parse("2026-08-24T08:00:00Z")
        )).isEqualTo(LocalDateTime.of(2026, 8, 24, 16, 0));
    }

    @Test
    void dstZoneConversionIsNotHardcodedPlusEightHours() {
        LocalDateTime wall = LocalDateTime.of(2026, 7, 1, 12, 0);
        Instant newYorkSummer = DatabaseTimes.toInstant(
                wall,
                ZoneId.of("America/New_York")
        );
        Instant hardcodedPlusEight = wall.atOffset(ZoneOffset.ofHours(8)).toInstant();
        assertThat(newYorkSummer).isEqualTo(Instant.parse("2026-07-01T16:00:00Z"));
        assertThat(newYorkSummer).isNotEqualTo(hardcodedPlusEight);
        Instant newYorkWinter = DatabaseTimes.toInstant(
                LocalDateTime.of(2026, 1, 1, 12, 0),
                ZoneId.of("America/New_York")
        );
        assertThat(newYorkWinter).isEqualTo(Instant.parse("2026-01-01T17:00:00Z"));
        assertThat(newYorkWinter).isNotEqualTo(newYorkSummer);
    }
}
