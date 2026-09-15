package com.skytrace.backend.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * 当前库内 DATETIME / {@link LocalDateTime} 的语义是 Asia/Shanghai 墙钟。
 * JSON 若带 offset，先转到此时区再丢掉 offset；无 offset 则按本地墙钟解析。
 */
public final class DatabaseTimes {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private DatabaseTimes() {
    }

    public static LocalDateTime toDatabaseLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZONE);
    }

    public static Instant toInstant(LocalDateTime value) {
        return toInstant(value, ZONE);
    }

    /**
     * 包内重载只给单测对照有 DST 的 ZoneId，证明转换走 ZoneId 而不是写死 +8 小时。
     */
    static Instant toInstant(LocalDateTime value, ZoneId zone) {
        if (value == null) {
            return null;
        }
        return value.atZone(zone).toInstant();
    }

    public static LocalDateTime parseJsonLocalDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DateTimeParseException("eventTime is blank", String.valueOf(raw), 0);
        }
        String text = raw.trim();
        if (hasExplicitOffset(text)) {
            return OffsetDateTime.parse(text)
                    .atZoneSameInstant(ZONE)
                    .toLocalDateTime();
        }
        return LocalDateTime.parse(text);
    }

    static boolean hasExplicitOffset(String text) {
        int length = text.length();
        if (length == 0) {
            return false;
        }
        char last = text.charAt(length - 1);
        if (last == 'Z' || last == 'z') {
            return true;
        }
        return text.matches(".*[+-]\\d{2}:\\d{2}$");
    }
}
