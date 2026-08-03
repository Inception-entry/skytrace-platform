package com.skytrace.backend.ai.client;

import com.skytrace.backend.ai.dto.KnowledgeDeleteResponse;
import com.skytrace.backend.ai.dto.KnowledgeDocumentResponse;
import com.skytrace.backend.ai.dto.KnowledgeSearchRequest;
import com.skytrace.backend.ai.dto.KnowledgeSearchResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class AiKnowledgeClient {

    private final RestClient restClient;
    private final AiCallExecutor callExecutor;

    public AiKnowledgeClient(
            AiRestClientFactory restClientFactory,
            AiCallExecutor callExecutor) {
        this.restClient = restClientFactory.create();
        this.callExecutor = callExecutor;
    }

    public List<KnowledgeDocumentResponse> listDocuments() {
        String requestId = UUID.randomUUID().toString();
        List<KnowledgeDocumentResponse> response = callExecutor.execute(
                "knowledge_list",
                requestId,
                null,
                () -> restClient.get()
                        .uri("/api/knowledge/documents")
                        .header("X-Request-Id", requestId)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        })
        );
        return response == null ? List.of() : response;
    }

    public KnowledgeDocumentResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择需要上传的文档");
        }

        MultipartBodyBuilder body = new MultipartBodyBuilder();
        try {
            body.part(
                            "file",
                            new NamedByteArrayResource(
                                    file.getBytes(),
                                    file.getOriginalFilename()
                            )
                    )
                    .contentType(resolveContentType(file));
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法读取上传的文档", ex);
        }

        String requestId = UUID.randomUUID().toString();
        KnowledgeDocumentResponse response = callExecutor.execute(
                "knowledge_upload",
                requestId,
                null,
                () -> restClient.post()
                        .uri("/api/knowledge/documents")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(body.build())
                        .retrieve()
                        .body(KnowledgeDocumentResponse.class)
        );
        if (response == null) {
            throw new AiClientException(AiErrorCode.INVALID_RESPONSE);
        }
        return response;
    }

    public List<KnowledgeSearchResult> search(
            KnowledgeSearchRequest request) {
        String requestId = UUID.randomUUID().toString();
        List<KnowledgeSearchResult> response = callExecutor.execute(
                "knowledge_search",
                requestId,
                null,
                () -> restClient.post()
                        .uri("/api/knowledge/search")
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        })
        );
        return response == null ? List.of() : response;
    }

    public KnowledgeDeleteResponse delete(String documentId) {
        String requestId = UUID.randomUUID().toString();
        KnowledgeDeleteResponse response = callExecutor.execute(
                "knowledge_delete",
                requestId,
                null,
                () -> restClient.delete()
                        .uri(
                                "/api/knowledge/documents/{documentId}",
                                documentId
                        )
                        .header("X-Request-Id", requestId)
                        .retrieve()
                        .body(KnowledgeDeleteResponse.class)
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
            this.filename = filename == null ? "unnamed.txt" : filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
