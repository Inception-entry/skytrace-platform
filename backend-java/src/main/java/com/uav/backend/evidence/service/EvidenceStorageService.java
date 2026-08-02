package com.uav.backend.evidence.service;

import com.uav.backend.common.ConflictException;
import com.uav.backend.evidence.MinioProperties;
import com.uav.backend.evidence.domain.EvidenceAsset;
import com.uav.backend.evidence.dto.EvidenceUploadResponse;
import com.uav.backend.evidence.repository.EvidenceAssetRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnBean(MinioClient.class)
public class EvidenceStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "video/mp4",
            "video/webm"
    );

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final EvidenceAssetRepository repository;

    public EvidenceStorageService(
            MinioClient minioClient,
            MinioProperties properties,
            EvidenceAssetRepository repository) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.repository = repository;
    }

    @Transactional
    public EvidenceUploadResponse upload(
            MultipartFile file,
            String taskCode,
            String alarmEventCode) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("未上传证据文件");
        }
        String contentType = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "仅支持 jpg/png/webp 截图或 mp4/webm 视频"
            );
        }

        try {
            ensureBucket();
            String extension = extensionFor(contentType, file.getOriginalFilename());
            String prefix = (taskCode == null || taskCode.isBlank())
                    ? "unassigned"
                    : taskCode;
            String objectKey = prefix + "/" + UUID.randomUUID() + extension;

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(properties.getEvidenceBucket())
                                .object(objectKey)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(contentType)
                                .build()
                );
            }

            EvidenceAsset asset = new EvidenceAsset();
            asset.setObjectKey(objectKey);
            asset.setBucket(properties.getEvidenceBucket());
            asset.setContentType(contentType);
            asset.setOriginalFilename(file.getOriginalFilename());
            asset.setSizeBytes(file.getSize());
            asset.setTaskCode(blankToNull(taskCode));
            asset.setAlarmEventCode(blankToNull(alarmEventCode));
            repository.save(asset);

            return new EvidenceUploadResponse(
                    objectKey,
                    properties.getEvidenceBucket(),
                    contentType,
                    file.getSize(),
                    asset.getTaskCode(),
                    asset.getAlarmEventCode(),
                    "/files/" + properties.getEvidenceBucket() + "/" + objectKey
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ConflictException("证据上传失败: " + exception.getMessage());
        }
    }

    private void ensureBucket() throws Exception {
        String bucket = properties.getEvidenceBucket();
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucket).build()
            );
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [{
                        "Effect": "Allow",
                        "Principal": {"AWS": ["*"]},
                        "Action": ["s3:GetObject"],
                        "Resource": ["arn:aws:s3:::%s/*"]
                      }]
                    }
                    """.formatted(bucket);
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(policy)
                            .build()
            );
        }
    }

    private static String extensionFor(String contentType, String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            default -> ".jpg";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
