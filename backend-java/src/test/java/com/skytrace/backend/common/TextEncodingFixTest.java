package com.skytrace.backend.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TextEncodingFixTest {

    @Test
    void repairsLatin1StyleMojibake() {
        String broken = new String(
                "一号无人机".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1
        );
        assertThat(broken).doesNotContain("无人");
        assertThat(TextEncodingFix.repairMojibake(broken)).isEqualTo("一号无人机");
    }

    @Test
    void repairsCameraName() {
        String broken = new String(
                "一号固定摄像头".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1
        );
        assertThat(TextEncodingFix.repairMojibake(broken))
                .isEqualTo("一号固定摄像头");
    }

    @Test
    void repairsEuroMappedNullByteFromCp1252() {
        // byte 0x80 of UTF-8 "一" (E4 B8 80) often becomes € under Windows-1252
        String broken = "ä¸\u20ACå\u008F·æ—\u00A0äººæœº";
        assertThat(TextEncodingFix.repairMojibake(broken)).isEqualTo("一号无人机");
    }

    @Test
    void leavesCorrectCjkUnchanged() {
        assertThat(TextEncodingFix.repairMojibake("一号固定摄像头"))
                .isEqualTo("一号固定摄像头");
    }
}
