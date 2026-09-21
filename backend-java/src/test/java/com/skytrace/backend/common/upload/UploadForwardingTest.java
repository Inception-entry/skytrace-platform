package com.skytrace.backend.common.upload;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadForwardingTest {

    @Test
    void sniffUsesHeaderAndStreamReplaysFullBody() throws Exception {
        byte[] payload = new byte[800];
        payload[0] = (byte) 0xFF;
        payload[1] = (byte) 0xD8;
        payload[2] = (byte) 0xFF;
        Arrays.fill(payload, 3, payload.length, (byte) 7);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "x.bin",
                "application/octet-stream",
                payload
        );

        UploadMagic.Detected detected = UploadForwarding.sniff(
                file,
                UploadMagic.Kind.IMAGE
        );
        Resource body = UploadForwarding.streamedBody(file, "frame.jpg");

        assertThat(detected.contentType()).isEqualTo("image/jpeg");
        assertThat(detected.extension()).isEqualTo(".jpg");
        assertThat(body.getFilename()).isEqualTo("frame.jpg");
        assertThat(body.contentLength()).isEqualTo(payload.length);
        assertThat(body.getInputStream().readAllBytes()).isEqualTo(payload);
    }

    @Test
    void knowledgeHtmlIsRejectedFromHeaderAlone() {
        byte[] html = "<!DOCTYPE html>".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc.pdf",
                "application/pdf",
                html
        );

        assertThatThrownBy(() -> UploadForwarding.sniff(
                file,
                UploadMagic.Kind.KNOWLEDGE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅支持 PDF、Markdown 和 TXT 文档");
    }
}
