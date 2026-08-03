package com.skytrace.backend.ai.client;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Component
public class AiRestClientFactory {

    private final RestClient.Builder builder;
    private final AiClientProperties properties;

    public AiRestClientFactory(
            RestClient.Builder builder,
            AiClientProperties properties) {
        this.builder = builder;
        this.properties = properties;
    }

    public RestClient create() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                // Uvicorn serves HTTP/1.1 here. Disabling the JDK client's
                // clear-text HTTP/2 upgrade avoids a rejected h2c handshake.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return builder.clone()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
