package com.skytrace.backend.ai.client;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AiVisionClient {

    private final RestClient restClient;
    private final AiCallExecutor callExecutor;

    public AiVisionClient(
            AiRestClientFactory restClientFactory,
            AiCallExecutor callExecutor) {
        this.restClient = restClientFactory.create();
        this.callExecutor = callExecutor;
    }

    public Map<String, Object> analyze(
            MultipartFile file,
            String deviceCode,
            String taskCode,
            Double latitude,
            Double longitude,
            boolean publishAlarms,
            Integer maxAlarms) {
        return analyzeInternal(
                "/api/detections/analyze",
                "vision_analyze",
                file,
                "请选择需要识别的图片",
                "无法读取上传图片",
                "frame.jpg",
                deviceCode,
                taskCode,
                latitude,
                longitude,
                publishAlarms,
                maxAlarms,
                null,
                null
        );
    }

    public Map<String, Object> analyzeVideo(
            MultipartFile file,
            String deviceCode,
            String taskCode,
            Double latitude,
            Double longitude,
            boolean publishAlarms,
            Integer maxAlarms,
            Double frameIntervalSec,
            Integer maxFrames) {
        return analyzeInternal(
                "/api/detections/analyze-video",
                "vision_video_analyze",
                file,
                "请选择需要识别的视频",
                "无法读取上传视频",
                "clip.mp4",
                deviceCode,
                taskCode,
                latitude,
                longitude,
                publishAlarms,
                maxAlarms,
                frameIntervalSec,
                maxFrames
        );
    }

    private Map<String, Object> analyzeInternal(
            String uri,
            String operation,
            MultipartFile file,
            String emptyMessage,
            String readErrorMessage,
            String defaultFilename,
            String deviceCode,
            String taskCode,
            Double latitude,
            Double longitude,
            boolean publishAlarms,
            Integer maxAlarms,
            Double frameIntervalSec,
            Integer maxFrames) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        try {
            body.part(
                            "file",
                            new NamedByteArrayResource(
                                    file.getBytes(),
                                    file.getOriginalFilename() == null
                                            ? defaultFilename
                                            : file.getOriginalFilename()
                            )
                    )
                    .contentType(resolveContentType(file));
        } catch (IOException ex) {
            throw new IllegalArgumentException(readErrorMessage, ex);
        }

        body.part("deviceCode", deviceCode == null ? "UAV-001" : deviceCode);
        if (taskCode != null && !taskCode.isBlank()) {
            body.part("taskCode", taskCode);
        }
        if (latitude != null) {
            body.part("latitude", String.valueOf(latitude));
        }
        if (longitude != null) {
            body.part("longitude", String.valueOf(longitude));
        }
        body.part("publishAlarms", String.valueOf(publishAlarms));
        if (maxAlarms != null) {
            body.part("maxAlarms", String.valueOf(maxAlarms));
        }
        if (frameIntervalSec != null) {
            body.part("frameIntervalSec", String.valueOf(frameIntervalSec));
        }
        if (maxFrames != null) {
            body.part("maxFrames", String.valueOf(maxFrames));
        }

        String requestId = UUID.randomUUID().toString();
        Map<String, Object> response = callExecutor.execute(
                operation,
                requestId,
                null,
                () -> restClient.post()
                        .uri(uri)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body.build())
                        .retrieve()
                        .body(LinkedHashMap.class)
        );
        if (response == null) {
            throw new AiClientException(AiErrorCode.INVALID_RESPONSE);
        }
        return response;
    }

    private MediaType resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
    }

    private static final class NamedByteArrayResource
            extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename == null ? "frame.jpg" : filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
