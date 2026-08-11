package com.skytrace.backend.evidence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EvidenceManifestService {

    private final ObjectMapper objectMapper;

    public EvidenceManifestService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ArchivedEvidenceFile(
            String evidenceCode,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String contentHash,
            String taskCode,
            String alarmEventCode,
            String bucket,
            String objectKey,
            String archivePath
    ) {
    }

    public List<ArchivedEvidenceFile> describe(List<EvidenceAsset> assets) {
        return assets.stream()
                .map(asset -> new ArchivedEvidenceFile(
                        asset.getEvidenceCode(),
                        asset.getOriginalFilename(),
                        asset.getContentType(),
                        asset.getSizeBytes(),
                        asset.getContentHash(),
                        asset.getTaskCode(),
                        asset.getAlarmEventCode(),
                        asset.getBucket(),
                        asset.getObjectKey(),
                        "files/" + asset.getEvidenceCode()
                                + resolveExtension(asset)
                ))
                .toList();
    }

    public byte[] buildManifest(
            EvidenceArchiveJob job,
            List<ArchivedEvidenceFile> files) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("jobCode", job.getJobCode());
            root.put("scopeType", job.getScopeType().name());
            root.put("scopeValue", job.getScopeValue());
            root.put("exportedAt", Instant.now().toString());
            root.put("createdBy", job.getCreatedByName());
            root.put("totalFiles", files.size());
            root.put(
                    "totalBytes",
                    files.stream().mapToLong(ArchivedEvidenceFile::sizeBytes).sum()
            );

            List<Map<String, Object>> manifestFiles = new ArrayList<>();
            for (ArchivedEvidenceFile file : files) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("evidenceCode", file.evidenceCode());
                item.put("archivePath", file.archivePath());
                item.put("originalFilename", file.originalFilename());
                item.put("contentType", file.contentType());
                item.put("sizeBytes", file.sizeBytes());
                item.put("contentHash", file.contentHash());
                item.put("taskCode", file.taskCode());
                item.put("alarmEventCode", file.alarmEventCode());
                manifestFiles.add(item);
            }
            root.put("files", manifestFiles);
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(root);
        } catch (Exception exception) {
            throw new IllegalStateException("生成归档清单失败", exception);
        }
    }

    public byte[] buildChecksums(List<ArchivedEvidenceFile> files) {
        StringBuilder builder = new StringBuilder();
        for (ArchivedEvidenceFile file : files) {
            builder.append(stripPrefix(file.contentHash()))
                    .append("  ")
                    .append(file.archivePath())
                    .append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String resolveExtension(EvidenceAsset asset) {
        String filename = asset.getOriginalFilename();
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'))
                    .toLowerCase(Locale.ROOT);
        }
        return switch (asset.getContentType()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            default -> ".jpg";
        };
    }

    private static String stripPrefix(String value) {
        if (value == null) {
            return "";
        }
        return value.startsWith("sha256:") ? value.substring(7) : value;
    }
}
