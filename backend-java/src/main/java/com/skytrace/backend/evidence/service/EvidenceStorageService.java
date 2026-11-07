// ==================== 项目自定义异常与工具包 ====================
import com.skytrace.backend.common.ConflictException; // 自定义业务冲突异常（通常用于处理数据冲突、操作失败等并向前端抛出 409 状态码）
import com.skytrace.backend.common.upload.UploadMagic; // 上传文件魔数（Magic Number）检测工具类，用于通过文件头部二进制特征识别真实 MIME 类型
import com.skytrace.backend.evidence.MinioProperties;    // MinIO 配置属性绑定类（读取配置文件中的 endpoint、bucket、AK/SK 等）
import com.skytrace.backend.evidence.domain.EvidenceAssetType; // 证据资产类型枚举（根据文件类型分类如：图片、视频、文档等）

// ==================== MinIO Java SDK 相关类 ====================
import io.minio.BucketExistsArgs;          // 用于构建“检查存储桶是否存在”的参数对象
import io.minio.GetObjectArgs;             // 用于构建“获取对象（文件流）”的参数对象
import io.minio.GetPresignedObjectUrlArgs; // 用于构建“生成预签名访问 URL”的参数对象
import io.minio.MakeBucketArgs;          // 用于构建“创建存储桶”的参数对象
import io.minio.MinioClient;               // MinIO 核心客户端类，提供所有与对象存储交互的底层 API
import io.minio.PutObjectArgs;             // 用于构建“上传/写入对象”的参数对象
import io.minio.RemoveObjectArgs;          // 用于构建“删除对象”的参数对象
import io.minio.SetBucketPolicyArgs;       // 用于构建“设置存储桶访问策略（如公共读）”的参数对象
import io.minio.StatObjectArgs;            // 用于构建“获取对象元数据（文件状态）”的参数对象
import io.minio.UploadObjectArgs;          // 用于构建“上传本地文件到对象存储”的参数对象
import io.minio.errors.ErrorResponseException; // MinIO 服务端返回的特定错误异常（如 NoSuchKey、NoSuchBucket 等）

// ==================== Spring Framework 核心及 Boot 注解 ====================
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean; // 条件注解：仅当 Spring 容器中存在指定 Bean 时才实例化当前类
import org.springframework.stereotype.Service; // 声明当前类是一个 Spring Service 组件（业务层 Bean）
import org.springframework.web.multipart.MultipartFile; // Spring MVC 用于处理文件上传的对象封装

// ==================== Java 标准库（IO 流、NIO 文件、时间及集合） ====================
import java.io.ByteArrayInputStream; // 字节数组输入流，用于将 byte[] 包装为 InputStream 供 SDK 上传
import java.io.InputStream;        // Java 标准输入流抽象基类
import java.io.SequenceInputStream;  // 顺序输入流，用于将多个输入流（如魔数头部 + 剩余输入流）串联成一个流
import java.nio.file.Files;        // NIO 文件操作工具类（判断文件是否存在、是否为常规文件等）
import java.nio.file.Path;         // NIO 文件路径抽象接口
import java.time.Instant;          // Java 8 时间戳类（用于精确表示时间点，计算 URL 过期时间）
import java.util.LinkedHashMap;    // 有序的 Map 集合实现类（用于保持查询参数的组装顺序）
import java.util.Map;              // Map 映射接口
import java.util.UUID;             // 通用唯一识别码生成器（用于生成全局唯一的对象存储文件名）
import java.util.concurrent.TimeUnit; // 并发包中的时间单位枚举（如配置预签名 URL 过期的秒数）

/**
 * 证据文件存储服务组件
 * <p>
 * 负责与 MinIO 对象存储服务交互，提供文件的流式上传（带魔数自动检测）、
 * 预签名 URL 生成（支持内外网 Endpoint 分离）、对象存在性检查、删除及下载等核心功能。
 * </p>
 * 使用 {@link ConditionalOnBean} 注解，只有当容器中存在 {@link MinioClient} Bean 时才会激活本服务。
 */
@Service
@ConditionalOnBean(MinioClient.class)
public class EvidenceStorageService {

    /** 内部访问专用的 MinIO 客户端（用于常规的上传、下载、状态检查等高带宽/内网操作） */
    private final MinioClient minioClient;
    
    /** 用于生成预签名 URL 的专用 MinIO 客户端（若配了外网公网地址，则使用外网 Endpoint 构造，确保浏览器能正确访问） */
    private final MinioClient presignClient;
    
    /** MinIO 配置属性实体类，包含 Bucket 名称、Endpoint、AK/SK 等配置信息 */
    private final MinioProperties properties;

    /**
     * 构造函数：初始化 MinIO 客户端及预签名客户端
     * 
     * @param minioClient 注入的默认 MinIO 客户端
     * @param properties  MinIO 配置属性
     */
    public EvidenceStorageService(
            MinioClient minioClient,
            MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
        
        // 核心设计：如果内外网地址相同，直接复用注入的客户端；
        // 如果不同，则单独构建一个指向公共公网地址（publicEndpoint）的客户端，
        // 从而确保生成的签名 URL 中的 Host 是浏览器实际能访问的公网地址，同时在单元测试中也能被 Mock 捕获。
        this.presignClient = sameEndpoint(
                properties.getEndpoint(),
                properties.getPublicEndpoint()
        )
                ? minioClient
                : MinioClient.builder()
                        // 签名 URL 中的 host 必须是浏览器实际访问的公共地址
                        .endpoint(properties.getPublicEndpoint())
                        // 指定 Region 后，SDK 不会尝试连接公共地址进行冗余的区域探测
                        .region(properties.getRegion())
                        .credentials(
                                properties.getAccessKey(),
                                properties.getSecretKey()
                        )
                        .build();
    }

    /**
     * 存储对象元数据记录（Record），用于封装文件上传成功后的核心基础信息
     */
    public record StoredObject(
            String objectKey,           // 存储在 MinIO 中的唯一对象路径/Key
            String bucket,              // 存储所在的 Bucket 名称
            String contentType,         // 文件实际检测出来的 MIME 类型
            long sizeBytes,             // 文件大小（字节数）
            String originalFilename,    // 客户端上传时的原始文件名
            EvidenceAssetType assetType // 证据资产类型枚举（如：图片、视频、文档等）
    ) {
    }

    /**
     * 获取当前配置的默认证据存储 Bucket 名称
     */
    public String evidenceBucket() {
        return properties.getEvidenceBucket();
    }

    /**
     * 上传证据文件核心方法
     * <p>
     * 流程：
     * 1. 校验文件是否为空。
     * 2. 读取文件头部 16 字节通过魔数（Magic Number）识别真实文件类型，防止恶意伪造后缀。
     * 3. 确保目标 Bucket 存在及配置了访问策略。
     * 4. 组装 ObjectKey（按 taskCode 归类目录 + UUID + 真实后缀）。
     * 5. 将读取了头部的字节流与剩余流通过 {@link SequenceInputStream} 拼回并上传至 MinIO。
     * </p>
     * 
     * @param file     前端上传的 MultipartFile 文件对象
     * @param taskCode 任务编码（用作存储路径前缀，若为空则归入 unassigned）
     * @return 包含文件存储详情的 {@link StoredObject} 对象
     */
    public StoredObject store(
            MultipartFile file,
            String taskCode) {
        // 1. 校验文件有效性
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("未上传证据文件");
        }

        try {
            // 2. 根据 taskCode 确定存储前缀（子目录）
            String prefix = (taskCode == null || taskCode.isBlank())
                    ? "unassigned"
                    : taskCode.trim();

            // 3. 读取流并检测文件真实类型
            try (InputStream inputStream = file.getInputStream()) {
                // 读取前 16 字节用于魔数检测
                byte[] header = inputStream.readNBytes(16);
                UploadMagic.Detected detected = UploadMagic.inspect(
                        header,
                        UploadMagic.Kind.EVIDENCE
                );
                
                // 4. 确保存储桶存在并正确配置策略
                ensureBucket();
                
                // 5. 拼接生成唯一的对象存储 Key (格式: prefix/UUID.ext)
                String objectKey = prefix
                        + "/"
                        + UUID.randomUUID()
                        + detected.extension();
                        
                // 6. 构造组合流：将之前读走的 16 字节头部和剩余的输入流重新拼接起来，避免文件头部丢失
                InputStream body = new SequenceInputStream(
                        new ByteArrayInputStream(header),
                        inputStream
                );
                
                // 7. 执行 MinIO 文件上传
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(properties.getEvidenceBucket())
                                .object(objectKey)
                                .stream(body, file.getSize(), -1L)
                                .contentType(detected.contentType())
                                .build()
                );
                
                // 8. 返回封装好的存储对象元数据
                return new StoredObject(
                        objectKey,
                        properties.getEvidenceBucket(),
                        detected.contentType(),
                        file.getSize(),
                        file.getOriginalFilename(),
                        EvidenceAssetType.fromContentType(detected.contentType())
                );
            }
        } catch (IllegalArgumentException exception) {
            // 直接抛出非法的参数异常
            throw exception;
        } catch (Exception exception) {
            // 捕获其他底层异常并包装为业务冲突异常
            throw new ConflictException(
                    "证据上传失败: " + exception.getMessage()
            );
        }
    }

    /**
     * 生成带有时效性的文件 GET 预签名访问 URL（用于前端安全预览或下载）
     * 
     * @param bucket             Bucket 名称
     * @param objectKey          对象 Key
     * @param ttlSeconds         URL 过期时间（秒）
     * @param contentDisposition 响应头的 Content-Disposition（例如控制是内联预览还是强制下载附件）
     * @return 可直接访问的完整预签名 URL 字符串
     */
    public String createPresignedGetUrl(
        String bucket,
        String objectKey,
        int ttlSeconds,
        String contentDisposition) {
        try {
            GetPresignedObjectUrlArgs.Builder builder =
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(ttlSeconds, TimeUnit.SECONDS);
                            
            // 如果指定了内容处置头（如文件名重命名、下载等），则加入到签名查询参数中
            if (contentDisposition != null && !contentDisposition.isBlank()) {
                Map<String, String> extra = new LinkedHashMap<>();
                extra.put(
                        "response-content-disposition",
                        contentDisposition
                );
                builder.extraQueryParams(extra);
            }
            // 使用 presignClient（公网客户端）生成最终的 URL
            return presignClient.getPresignedObjectUrl(builder.build());
        } catch (Exception exception) {
            throw new ConflictException(
                    "生成证据访问地址失败: " + exception.getMessage()
            );
        }
    }

    /**
     * 计算指定 TTL 秒数之后的过期时间点
     * 
     * @param ttlSeconds 有效秒数
     * @return 过期时间 {@link Instant}
     */
    public Instant expiresAt(int ttlSeconds) {
        return Instant.now().plusSeconds(ttlSeconds);
    }

    /** 获取属性配置中定义的预览 URL 默认存活时间（秒） */
    public int previewTtlSeconds() {
        return properties.getPresignPreviewTtlSeconds();
    }

    /** 获取属性配置中定义的下载 URL 默认存活时间（秒） */
    public int downloadTtlSeconds() {
        return properties.getPresignDownloadTtlSeconds();
    }

    /**
     * 生成旧版遗留系统的公共访问路径（伪路径）
     */
    public String legacyPublicPath(String bucket, String objectKey) {
        return "/files/" + bucket + "/" + objectKey;
    }

    /**
     * 读取指定对象的全部字节内容
     * 
     * @param bucket    Bucket 名称
     * @param objectKey 对象 Key
     * @return 文件的 byte[] 数组
     */
    public byte[] getObjectBytes(String bucket, String objectKey) {
        try (InputStream stream = getObjectStream(bucket, objectKey)) {
            return stream.readAllBytes();
        } catch (Exception exception) {
            throw new ConflictException(
                    "读取证据对象失败: " + exception.getMessage()
            );
        }
    }

    /**
     * 获取指定对象的输入流（用于服务端读取或转发）
     * 
     * @param bucket    Bucket 名称
     * @param objectKey 对象 Key
     * @return 文件的 {@link InputStream} 输入流
     */
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

    /**
     * 将字节数组直接写入（put）到 MinIO 指定对象中（常用于生成缩略图、衍生报表等场景）
     * 
     * @param bucket      Bucket 名称
     * @param objectKey   目标对象 Key
     * @param bytes       待写入的字节数组
     * @param contentType 对象的 MIME 类型
     */
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
                                    (long) bytes.length, // 显式转换为 long 解决类型不匹配
                                    -1L                  // partSize 设为 -1L 采用默认分片大小
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

    /**
     * 将本地文件直接上传到 MinIO (常用于归档文件上传)
     * 
     * @param bucket      Bucket 名称
     * @param objectKey   目标对象 Key
     * @param sourceFile  本地文件 {@link Path} 路径
     * @param contentType 对象的 MIME 类型
     */
    public void uploadObject(
            String bucket,
            String objectKey,
            Path sourceFile,
            String contentType) {
        // 校验本地文件是否存在且为常规文件
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            throw new IllegalArgumentException("待上传的本地文件不存在");
        }

        try {
            ensureBucket();
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .filename(sourceFile.toAbsolutePath().toString())
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new ConflictException(
                    "上传本地归档文件失败: " + exception.getMessage()
            );
        }
    }

    /**
     * 检查指定的对象在 MinIO 中是否存在
     * 
     * @param bucket    Bucket 名称
     * @param objectKey 对象 Key
     * @return 存在返回 true，否则返回 false
     */
    public boolean objectExists(String bucket, String objectKey) {
        try {
            // 通过获取对象元数据（statObject）来判定是否存在
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (ErrorResponseException exception) {
            String code = exception.errorResponse().code();
            // 如果错误码属于“找不到 Key/Object/Bucket”，则认为文件不存在，返回 false
            if ("NoSuchKey".equals(code)
                    || "NoSuchObject".equals(code)
                    || "NoSuchBucket".equals(code)) {
                return false;
            }
            throw new ConflictException(
                    "检查证据对象失败: " + exception.getMessage()
            );
        } catch (Exception exception) {
            throw new ConflictException(
                    "检查证据对象失败: " + exception.getMessage()
            );
        }
    }

    /**
     * 物理删除指定的证据对象
     * <p>
     * 保护机制：如果 objectKey 以 "archives/" 开头，属于归档产物，触发安全拦截禁止删除。
     * </p>
     * 
     * @param bucket    Bucket 名称
     * @param objectKey 对象 Key
     */
    public void removeEvidenceObject(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        // 安全拦截：清理任务禁止删除归档产物
        if (objectKey.startsWith("archives/")) {
            throw new IllegalArgumentException("清理任务禁止删除归档产物");
        }

        try {
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

    /**
     * 私有辅助方法：确保目标 Bucket 存在；若不存在则自动创建；
     * 若配置了公共读权限（publicReadEnabled），则自动应用公开的 S3 Bucket Policy 策略。
     */
    private void ensureBucket() throws Exception {
        String bucket = properties.getEvidenceBucket();
        // 1. 检查 Bucket 是否已存在
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build()
        );
        // 2. 不存在则创建
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucket).build()
            );
        }
        // 3. 如果开启了公共读取权限，配置相应的 JSON 访问策略
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

    /**
     * 对比内部和外部 Endpoint 是否相同（用于判断是否需要构建独立的 presignClient）
     */
    private static boolean sameEndpoint(String internal, String external) {
        return normalizeEndpoint(internal).equals(normalizeEndpoint(external));
    }

    /**
     * 规范化 Endpoint 地址（去除末尾可能多余的斜杠 /）
     */
    private static String normalizeEndpoint(String endpoint) {
        if (endpoint == null) {
            return "";
        }
        return endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
    }
}