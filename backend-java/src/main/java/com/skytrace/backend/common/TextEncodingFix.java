package com.skytrace.backend.common;

import java.nio.charset.StandardCharsets;

/**
 * Repair UTF-8 text that was incorrectly decoded as Latin-1 / Windows-1252
 * (classic JDBC {@code characterEncoding=utf8} mojibake on some JVMs).
 */
public final class TextEncodingFix {

    private TextEncodingFix() {}

    public static String repairMojibake(String value) {
        if (value == null || value.isEmpty() || containsCjk(value)) {
            return value;
        }
        try {
            byte[] bytes = toOriginalBytes(value);
            String candidate = new String(bytes, StandardCharsets.UTF_8);
            if (containsCjk(candidate) && !candidate.contains("\uFFFD")) {
                return candidate;
            }
        } catch (Exception ignored) {
            // keep original
        }
        return value;
    }

    /**
     * Reverse the hybrid Windows-1252 / Latin-1 decoding used when UTF-8
     * bytes were misinterpreted: code points &lt;= 0xFF map to themselves,
     * while cp1252 specials like € map back to 0x80-0x9F.
     */
    private static byte[] toOriginalBytes(String value) {
        byte[] bytes = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            bytes[i] = charToByte(c);
        }
        return bytes;
    }

    private static byte charToByte(char c) {
        if (c <= 0xFF) {
            return (byte) c;
        }
        return switch (c) {
            case '\u20AC' -> (byte) 0x80; // €
            case '\u201A' -> (byte) 0x82;
            case '\u0192' -> (byte) 0x83;
            case '\u201E' -> (byte) 0x84;
            case '\u2026' -> (byte) 0x85;
            case '\u2020' -> (byte) 0x86;
            case '\u2021' -> (byte) 0x87;
            case '\u02C6' -> (byte) 0x88;
            case '\u2030' -> (byte) 0x89;
            case '\u0160' -> (byte) 0x8A;
            case '\u2039' -> (byte) 0x8B;
            case '\u0152' -> (byte) 0x8C;
            case '\u017D' -> (byte) 0x8E;
            case '\u2018' -> (byte) 0x91;
            case '\u2019' -> (byte) 0x92;
            case '\u201C' -> (byte) 0x93;
            case '\u201D' -> (byte) 0x94;
            case '\u2022' -> (byte) 0x95;
            case '\u2013' -> (byte) 0x96;
            case '\u2014' -> (byte) 0x97;
            case '\u02DC' -> (byte) 0x98;
            case '\u2122' -> (byte) 0x99;
            case '\u0161' -> (byte) 0x9A;
            case '\u203A' -> (byte) 0x9B;
            case '\u0153' -> (byte) 0x9C;
            case '\u017E' -> (byte) 0x9E;
            case '\u0178' -> (byte) 0x9F;
            default -> throw new IllegalArgumentException(
                    "unmappable mojibake char U+" + Integer.toHexString(c)
            );
        };
    }

    private static boolean containsCjk(String value) {
        return value.codePoints().anyMatch(
                cp -> cp >= 0x4E00 && cp <= 0x9FFF
        );
    }
}
