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
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final MinioClient presignClient;
    private final MinioProperties properties;

    public EvidenceStorageService(
            MinioClient minioClient,
            MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
        // 内外地址相同时复用注入的客户端，单元测试也能继续捕获 Mock 调用。
        this.presignClient = sameEndpoint(
                properties.getEndpoint(),
                properties.getPublicEndpoint()
        )
                ? minioClient
                : MinioClient.builder()
                        // 签名 URL 中的 host 必须是浏览器实际访问的公共地址。
                        .endpoint(properties.getPublicEndpoint())
                        // 指定 Region 后，SDK 不会尝试连接公共地址进行区域探测。
                        .region(properties.getRegion())
                        .credentials(
                                properties.getAccessKey(),
                                properties.getSecretKey()
                        )
                        .build();
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
            return presignClient.getPresignedObjectUrl(builder.build());
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

    public void uploadObject(
            String bucket,
            String objectKey,
            Path sourceFile,
            String contentType) {
        // 在调用 MinIO 前先确认临时文件真实存在，避免把路径错误包装成难懂的 SDK 异常。
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException("待上传的本地文件不存在");
        }

        try {
            // 上传前沿用现有逻辑确保目标证据桶已经创建。
            ensureBucket();
            // uploadObject 让 MinIO SDK 直接读取磁盘文件，不需要先构造 byte[]。
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .filename(sourceFile.toAbsolutePath().toString())
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            // 统一转换成业务异常，让上层归档 Activity 可以记录失败状态并由 Temporal 重试。
            throw new ConflictException(
                    "上传本地归档文件失败: " + exception.getMessage()
            );
        }
    }

    public boolean objectExists(String bucket, String objectKey) {
        try {
            // statObject 只读取对象元数据，不会把对象内容下载到应用。
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            // SDK 正常返回说明对象存在且调用方拥有读取权限。
            return true;
        } catch (ErrorResponseException exception) {
            // MinIO 对不存在对象可能返回以下任一兼容 S3 的错误码。
            String code = exception.errorResponse().code();
            if ("NoSuchKey".equals(code)
                    || "NoSuchObject".equals(code)
                    || "NoSuchBucket".equals(code)) {
                return false;
            }
            // 权限、签名或服务端错误不能伪装成“不存在”，否则清理判断会失真。
            throw new ConflictException(
                    "检查证据对象失败: " + exception.getMessage()
            );
        } catch (Exception exception) {
            throw new ConflictException(
                    "检查证据对象失败: " + exception.getMessage()
            );
        }
    }

    public void removeEvidenceObject(String bucket, String objectKey) {
        // 派生对象字段可能为空，清理调用方可以直接跳过而无需重复判空。
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        // 永远禁止维护任务通过这个方法删除 archives/ 下的归档保全产物。
        if (objectKey.startsWith("archives/")) {
            throw new IllegalArgumentException("清理任务禁止删除归档产物");
        }

        try {
            // S3 删除不存在对象具有幂等语义，部分失败后的重试可以安全继续。
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new ConflictException(
                    "物理删除证据对象失败: " + exception.getMessage()
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

    private static boolean sameEndpoint(String internal, String external) {
        // 去掉末尾斜杠后再比较，避免纯格式差异创建第二个客户端。
        return normalizeEndpoint(internal).equals(normalizeEndpoint(external));
    }

    private static String normalizeEndpoint(String endpoint) {
        // publicEndpoint 未配置时回退为空字符串，由配置默认值保证生产路径可用。
        if (endpoint == null) {
            return "";
        }
        // MinIO endpoint 本身不需要尾部斜杠，统一后便于稳定比较。
        return endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
    }
}
