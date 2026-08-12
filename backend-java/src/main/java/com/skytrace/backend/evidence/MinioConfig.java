package com.skytrace.backend.evidence;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
@ConditionalOnProperty(
        name = "app.minio.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        URI uri = URI.create(properties.getEndpoint());
        String endpoint = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        // 连接失败需要快速返回给 Temporal；已经建立的读写仍允许处理大对象。
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(
                        properties.getConnectTimeout().toMillis(),
                        TimeUnit.MILLISECONDS
                )
                .readTimeout(
                        properties.getReadTimeout().toMillis(),
                        TimeUnit.MILLISECONDS
                )
                .writeTimeout(
                        properties.getWriteTimeout().toMillis(),
                        TimeUnit.MILLISECONDS
                )
                .build();
        return MinioClient.builder()
                .endpoint(endpoint)
                // 固定 Region 后，普通读写和公共 URL 签名使用完全相同的范围。
                .region(properties.getRegion())
                .credentials(
                        properties.getAccessKey(),
                        properties.getSecretKey()
                )
                // 传入受控 OkHttpClient，覆盖 MinIO SDK 五分钟的默认连接超时。
                .httpClient(httpClient)
                .build();
    }
}
