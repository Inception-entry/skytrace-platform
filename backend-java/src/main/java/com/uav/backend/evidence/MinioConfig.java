package com.uav.backend.evidence;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

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
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(
                        properties.getAccessKey(),
                        properties.getSecretKey()
                )
                .build();
    }
}
