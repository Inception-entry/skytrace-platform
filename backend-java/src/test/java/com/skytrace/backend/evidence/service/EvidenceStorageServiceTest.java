package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.UploadObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceStorageServiceTest {

    // JUnit 为文件上传测试提供隔离的临时目录。
    @TempDir
    Path tempDirectory;

    private final MinioClient minioClient = mock(MinioClient.class);
    private final MinioProperties properties = new MinioProperties();
    private EvidenceStorageService service;

    @BeforeEach
    void setUp() {
        properties.setEvidenceBucket("evidence");
        service = new EvidenceStorageService(minioClient, properties);
    }

    @Test
    void shouldBuildLegacyPublicPath() {
        assertThat(service.legacyPublicPath("evidence", "TASK-001/demo.jpg"))
                .isEqualTo("/files/evidence/TASK-001/demo.jpg");
    }

    @Test
    void shouldExposeConfiguredTtl() {
        properties.setPresignPreviewTtlSeconds(120);
        properties.setPresignDownloadTtlSeconds(180);
        assertThat(service.previewTtlSeconds()).isEqualTo(120);
        assertThat(service.downloadTtlSeconds()).isEqualTo(180);
    }

    @Test
    void shouldUploadLocalFileWithoutReadingItIntoByteArray()
            throws Exception {
        // 创建一个真实的小文件，模拟归档服务已经写完的磁盘 ZIP。
        Path archiveFile = tempDirectory.resolve("ARCHIVE-001.zip");
        // 文件内容本身不重要，这里只需确保 Files.isRegularFile 校验通过。
        Files.writeString(archiveFile, "zip-content");
        // 模拟证据桶已经存在，避免测试进入创建桶分支。
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenReturn(true);

        // 调用新增的路径上传方法，生产中 MinIO SDK 会直接读取这个文件。
        service.uploadObject(
                "evidence",
                "archives/ARCHIVE-001/ARCHIVE-001.zip",
                archiveFile,
                "application/zip"
        );

        // 捕获 SDK 参数，确认传递的是本地文件名而不是内存 byte[]。
        ArgumentCaptor<UploadObjectArgs> argumentCaptor =
                ArgumentCaptor.forClass(UploadObjectArgs.class);
        // MinIO 的 uploadObject 应当只接收到一次文件上传请求。
        verify(minioClient).uploadObject(argumentCaptor.capture());
        // 取出实际参数，逐项核对对象位置和内容类型。
        UploadObjectArgs uploadArgs = argumentCaptor.getValue();
        // 桶名沿用归档文件所属的 evidence 桶。
        assertThat(uploadArgs.bucket()).isEqualTo("evidence");
        // 对象键由归档任务号组成，重试时会覆盖同一位置。
        assertThat(uploadArgs.object())
                .isEqualTo("archives/ARCHIVE-001/ARCHIVE-001.zip");
        // filename 是绝对路径，SDK 可以直接从磁盘分段读取。
        assertThat(uploadArgs.filename())
                .isEqualTo(archiveFile.toAbsolutePath().toString());
        // ZIP 对象需要正确的 HTTP 内容类型。
        assertThat(uploadArgs.contentType()).isEqualTo("application/zip");
    }

    @Test
    void shouldUseBrowserReachableEndpointForPresignedUrl() {
        // 后端对象读写仍走容器内地址，签名链接改用浏览器可达域名。
        properties.setEndpoint("http://minio:9000");
        properties.setPublicEndpoint("https://files.example.test");
        service = new EvidenceStorageService(minioClient, properties);

        // 生成 presigned URL 只做本地签名，不会向测试域名发网络请求。
        String url = service.createPresignedGetUrl(
                "evidence",
                "archives/AR-001/AR-001.zip",
                300,
                null
        );

        // 返回给浏览器的 authority 不能泄漏 Docker 内部主机名 minio:9000。
        assertThat(url).startsWith(
                "https://files.example.test/evidence/archives/AR-001/AR-001.zip?"
        );
    }

    @Test
    void shouldCheckObjectMetadataWithoutDownloadingContent()
            throws Exception {
        // Mock 的 statObject 正常返回即表示对象存在。
        assertThat(service.objectExists(
                "evidence",
                "archives/AR-001/manifest.json"
        )).isTrue();

        // 只允许调用元数据接口，清理校验不应下载 manifest 内容。
        ArgumentCaptor<StatObjectArgs> argumentCaptor =
                ArgumentCaptor.forClass(StatObjectArgs.class);
        verify(minioClient).statObject(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().bucket()).isEqualTo("evidence");
        assertThat(argumentCaptor.getValue().object())
                .isEqualTo("archives/AR-001/manifest.json");
    }

    @Test
    void shouldDeleteEvidenceObjectButRejectArchiveObject()
            throws Exception {
        // 普通原始对象通过 MinIO 幂等删除接口处理。
        service.removeEvidenceObject("evidence", "TASK-001/source.jpg");

        ArgumentCaptor<RemoveObjectArgs> argumentCaptor =
                ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue().bucket()).isEqualTo("evidence");
        assertThat(argumentCaptor.getValue().object())
                .isEqualTo("TASK-001/source.jpg");

        // 维护边界硬编码保护 archives/，配置错误也不能删除归档保全产物。
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.removeEvidenceObject(
                        "evidence",
                        "archives/AR-001/AR-001.zip"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("清理任务禁止删除归档产物");
    }
}
