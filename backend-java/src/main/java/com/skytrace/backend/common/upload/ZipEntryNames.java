package com.skytrace.backend.common.upload;

/**
 * ZIP 条目只允许相对路径。拒绝绝对路径、盘符、空字节和 {@code ..}，避免 Zip Slip。
 */
public final class ZipEntryNames {

    private ZipEntryNames() {
    }

    public static String requireSafe(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ZIP 条目名不能为空");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.indexOf('\0') >= 0
                || normalized.startsWith("/")
                || normalized.startsWith("../")
                || normalized.startsWith("./")
                || normalized.contains("/../")
                || normalized.contains("/./")
                || normalized.endsWith("/..")
                || normalized.equals("..")
                || normalized.contains(":")) {
            throw new IllegalArgumentException("ZIP 条目路径不安全");
        }
        return normalized;
    }
}
