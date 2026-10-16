package com.skytrace.backend.common.upload;

/**
 * 下载文件名只保留安全字符，避免 Content-Disposition 注入换行或引号。
 */
public final class ContentDispositionNames {

    private ContentDispositionNames() {
    }

    public static String attachment(String filename) {
        return "attachment; filename=\"" + safe(filename) + "\"";
    }

    public static String inline(String filename) {
        return "inline; filename=\"" + safe(filename) + "\"";
    }

    static String safe(String filename) {
        String base = filename == null ? "" : filename;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned)) {
            return "download";
        }
        return cleaned;
    }
}
