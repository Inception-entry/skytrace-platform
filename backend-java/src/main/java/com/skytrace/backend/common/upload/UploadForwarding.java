package com.skytrace.backend.common.upload;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Sniffs a short header, then streams the original multipart body.
 * Callers must not use {@link MultipartFile#getBytes()} for AI forwarding.
 */
public final class UploadForwarding {

    static final int SNIFF_BYTES = 512;

    private UploadForwarding() {
    }

    public static UploadMagic.Detected sniff(
            MultipartFile file,
            UploadMagic.Kind kind) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(SNIFF_BYTES);
            return UploadMagic.inspect(
                    header,
                    kind,
                    file.getOriginalFilename()
            );
        }
    }

    public static Resource streamedBody(
            MultipartFile file,
            String filename) throws IOException {
        return new NamedInputStreamResource(
                file.getInputStream(),
                filename,
                file.getSize()
        );
    }

    static final class NamedInputStreamResource extends InputStreamResource {

        private final String filename;
        private final long contentLength;

        NamedInputStreamResource(
                InputStream inputStream,
                String filename,
                long contentLength) {
            super(inputStream);
            this.filename = filename;
            this.contentLength = contentLength;
        }

        @Override
        public String getFilename() {
            return filename;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }
    }
}
