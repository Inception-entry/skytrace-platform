package com.skytrace.backend.common.upload;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadMagicTest {

    @Test
    void jpegIsDetectedEvenWhenNamedPhpJpg() {
        UploadMagic.Detected detected = UploadMagic.inspect(
                jpegBytes(),
                UploadMagic.Kind.EVIDENCE,
                "x.php.jpg"
        );

        assertThat(detected.contentType()).isEqualTo("image/jpeg");
        assertThat(detected.extension()).isEqualTo(".jpg");
    }

    @Test
    void htmlClaimingToBeJpegIsRejected() {
        assertThatThrownBy(() -> UploadMagic.inspect(
                "<html><body>hi</body></html>".getBytes(StandardCharsets.US_ASCII),
                UploadMagic.Kind.EVIDENCE,
                "avatar.jpg"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅支持 jpg/png/webp 截图或 mp4/webm 视频");
    }

    @Test
    void pdfMagicIsRequiredForKnowledgePdf() {
        UploadMagic.Detected detected = UploadMagic.inspect(
                "%PDF-1.4 fake".getBytes(StandardCharsets.US_ASCII),
                UploadMagic.Kind.KNOWLEDGE,
                "notes.php.pdf"
        );

        assertThat(detected.contentType()).isEqualTo("application/pdf");
        assertThat(detected.extension()).isEqualTo(".pdf");
    }

    @Test
    void htmlNamedPdfIsRejected() {
        assertThatThrownBy(() -> UploadMagic.inspect(
                "<!DOCTYPE html>".getBytes(StandardCharsets.US_ASCII),
                UploadMagic.Kind.KNOWLEDGE,
                "doc.pdf"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅支持 PDF、Markdown 和 TXT 文档");
    }

    @Test
    void markdownIsAllowedByExtensionWhenNotHtml() {
        UploadMagic.Detected detected = UploadMagic.inspect(
                "# title\n".getBytes(StandardCharsets.UTF_8),
                UploadMagic.Kind.KNOWLEDGE,
                "guide.md"
        );

        assertThat(detected.contentType()).isEqualTo("text/markdown");
        assertThat(detected.extension()).isEqualTo(".md");
    }

    @Test
    void jpegIsRejectedAsVisionVideo() {
        assertThatThrownBy(() -> UploadMagic.inspect(
                jpegBytes(),
                UploadMagic.Kind.VIDEO
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅支持 mp4/webm 视频");
    }

    @Test
    void mp4AndWebmAreDetected() {
        assertThat(UploadMagic.inspect(mp4Bytes(), UploadMagic.Kind.VIDEO).extension())
                .isEqualTo(".mp4");
        assertThat(UploadMagic.inspect(webmBytes(), UploadMagic.Kind.VIDEO).extension())
                .isEqualTo(".webm");
    }

    @Test
    void emptyBytesAreRejected() {
        assertThatThrownBy(() -> UploadMagic.inspect(
                new byte[0],
                UploadMagic.Kind.IMAGE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文件为空");
    }

    private static byte[] jpegBytes() {
        return new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1
        };
    }

    private static byte[] mp4Bytes() {
        return new byte[] {
                0, 0, 0, 24,
                'f', 't', 'y', 'p',
                'i', 's', 'o', 'm',
                0, 0, 0, 0
        };
    }

    private static byte[] webmBytes() {
        return new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 1, 2, 3, 4};
    }
}
