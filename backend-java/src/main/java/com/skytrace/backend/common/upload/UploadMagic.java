package com.skytrace.backend.common.upload;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Sniffs public upload bytes. Client MIME and original filenames are not trusted.
 */
public final class UploadMagic {

    public enum Kind {
        EVIDENCE,
        KNOWLEDGE,
        IMAGE,
        VIDEO
    }

    public record Detected(String contentType, String extension) {
    }

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final byte[] FTYP = {'f', 't', 'y', 'p'};
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private UploadMagic() {
    }

    public static Detected inspect(byte[] header, Kind kind) {
        return inspect(header, kind, null);
    }

    public static Detected inspect(
            byte[] header,
            Kind kind,
            String originalFilename) {
        if (header == null || header.length == 0) {
            throw new IllegalArgumentException("文件为空");
        }
        return switch (kind) {
            case EVIDENCE -> firstMatch(
                    detectImage(header),
                    detectVideo(header),
                    "仅支持 jpg/png/webp 截图或 mp4/webm 视频"
            );
            case IMAGE -> firstMatch(
                    detectImage(header),
                    null,
                    "仅支持 jpg/png/webp 图片"
            );
            case VIDEO -> firstMatch(
                    detectVideo(header),
                    null,
                    "仅支持 mp4/webm 视频"
            );
            case KNOWLEDGE -> detectKnowledge(header, originalFilename);
        };
    }

    public static String extensionForContentType(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "application/pdf" -> ".pdf";
            case "text/markdown" -> ".md";
            case "text/plain" -> ".txt";
            default -> ".jpg";
        };
    }

    private static Detected firstMatch(
            Detected primary,
            Detected secondary,
            String message) {
        if (primary != null) {
            return primary;
        }
        if (secondary != null) {
            return secondary;
        }
        throw new IllegalArgumentException(message);
    }

    private static Detected detectImage(byte[] header) {
        int offset = skipBom(header);
        if (startsWith(header, offset, JPEG)) {
            return new Detected("image/jpeg", ".jpg");
        }
        if (startsWith(header, offset, PNG)) {
            return new Detected("image/png", ".png");
        }
        if (startsWith(header, offset, RIFF)
                && startsWith(header, offset + 8, WEBP)) {
            return new Detected("image/webp", ".webp");
        }
        return null;
    }

    private static Detected detectVideo(byte[] header) {
        int offset = skipBom(header);
        if (startsWith(header, offset + 4, FTYP)) {
            return new Detected("video/mp4", ".mp4");
        }
        if (startsWith(header, offset, WEBM)) {
            return new Detected("video/webm", ".webm");
        }
        return null;
    }

    private static Detected detectKnowledge(
            byte[] header,
            String originalFilename) {
        int offset = skipBom(header);
        if (startsWith(header, offset, PDF)) {
            return new Detected("application/pdf", ".pdf");
        }
        if (looksLikeHtml(header, offset) || containsNul(header)) {
            throw new IllegalArgumentException("仅支持 PDF、Markdown 和 TXT 文档");
        }
        String extension = extensionOf(originalFilename);
        if (".md".equals(extension) || ".markdown".equals(extension)) {
            return new Detected("text/markdown", extension);
        }
        if (".txt".equals(extension)) {
            return new Detected("text/plain", ".txt");
        }
        throw new IllegalArgumentException("仅支持 PDF、Markdown 和 TXT 文档");
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeHtml(byte[] header, int offset) {
        int index = offset;
        while (index < header.length && (header[index] & 0xFF) <= 0x20) {
            index++;
        }
        int length = Math.min(32, header.length - index);
        if (length <= 0) {
            return false;
        }
        String prefix = new String(
                header,
                index,
                length,
                StandardCharsets.US_ASCII
        ).toLowerCase(Locale.ROOT);
        return prefix.startsWith("<!doctype html")
                || prefix.startsWith("<html")
                || prefix.startsWith("<svg")
                || prefix.startsWith("<script");
    }

    private static boolean containsNul(byte[] header) {
        int limit = Math.min(header.length, 512);
        for (int i = 0; i < limit; i++) {
            if (header[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static int skipBom(byte[] header) {
        return startsWith(header, 0, UTF8_BOM) ? 3 : 0;
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (offset < 0 || data.length - offset < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
