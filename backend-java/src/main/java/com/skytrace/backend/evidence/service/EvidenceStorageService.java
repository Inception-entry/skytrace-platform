package com.skytrace.backend.evidence.service;

import com.skytrace.backend.common.ConflictException;
import com.skytrace.backend.evidence.MinioProperties;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    public EvidenceStorageService(
            MinioClient minioClient,
            MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public record StoredObject(
            String objectKey,
            String bucket,
            String contentType,
            long sizeBytes,
            String originalFilename,
            EvidenceAssetType assetType
    ) {
    }

    public StoredObject store(
            MultipartFile file,
            String taskCode) {
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
            String extension = extensionFor(
                    contentType,
                    file.getOriginalFilename()
            );
            String prefix = (taskCode == null || taskCode.isBlank())
                    ? "unassigned"
                    : taskCode.trim();
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

            return new StoredObject(
                    objectKey,
                    properties.getEvidenceBucket(),
                    contentType,
                    file.getSize(),
                    file.getOriginalFilename(),
                    EvidenceAssetType.fromContentType(contentType)
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ConflictException(
                    "证据上传失败: " + exception.getMessage()
            );
        }
    }

    public String createPresignedGetUrl(
            String bucket,
            String objectKey,
            int ttlSeconds,
            String contentDisposition) {
        try {
            GetPresignedObjectUrlArgs.Builder builder =
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(ttlSeconds, TimeUnit.SECONDS);
            if (contentDisposition != null && !contentDisposition.isBlank()) {
                Map<String, String> extra = new LinkedHashMap<>();
                extra.put(
                        "response-content-disposition",
                        contentDisposition
                );
                builder.extraQueryParams(extra);
            }
            return minioClient.getPresignedObjectUrl(builder.build());
        } catch (Exception exception) {
            throw new ConflictException(
                    "生成证据访问地址失败: " + exception.getMessage()
            );
        }
    }

    public Instant expiresAt(int ttlSeconds) {
        return Instant.now().plusSeconds(ttlSeconds);
    }

    public int previewTtlSeconds() {
        return properties.getPresignPreviewTtlSeconds();
    }

    public int downloadTtlSeconds() {
        return properties.getPresignDownloadTtlSeconds();
    }

    public String legacyPublicPath(String bucket, String objectKey) {
        return "/files/" + bucket + "/" + objectKey;
    }

    public byte[] getObjectBytes(String bucket, String objectKey) {
        try (InputStream stream = getObjectStream(bucket, objectKey)) {
            return stream.readAllBytes();
        } catch (Exception exception) {
            throw new ConflictException(
                    "读取证据对象失败: " + exception.getMessage()
            );
        }
    }

    public InputStream getObjectStream(String bucket, String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new ConflictException(
                    "读取证据对象失败: " + exception.getMessage()
            );
        }
    }

    public void putObject(
            String bucket,
            String objectKey,
            byte[] bytes,
            String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(
                                    new ByteArrayInputStream(bytes),
                                    bytes.length,
                                    -1
                            )
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new ConflictException(
                    "写入衍生对象失败: " + exception.getMessage()
            );
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
        }
        if (properties.isPublicReadEnabled()) {
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
}
