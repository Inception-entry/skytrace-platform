package com.skytrace.backend.ai.client;

import com.skytrace.backend.ai.dto.KnowledgeDeleteResponse;
import com.skytrace.backend.ai.dto.KnowledgeDocumentResponse;
import com.skytrace.backend.ai.dto.KnowledgeSearchRequest;
import com.skytrace.backend.ai.dto.KnowledgeSearchResult;
import com.skytrace.backend.common.upload.UploadForwarding;
import com.skytrace.backend.common.upload.UploadMagic;
import org.springframework.core.ParameterizedTypeReference;
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

        UploadMagic.Detected detected;
        try {
            detected = UploadForwarding.sniff(file, UploadMagic.Kind.KNOWLEDGE);
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法读取上传的文档", ex);
        }
        String filename = "document" + detected.extension();
        MediaType fileType = MediaType.parseMediaType(detected.contentType());

        String requestId = UUID.randomUUID().toString();
        KnowledgeDocumentResponse response = callExecutor.execute(
                "knowledge_upload",
                requestId,
                null,
                () -> {
                    MultipartBodyBuilder body = new MultipartBodyBuilder();
                    try {
                        body.part(
                                        "file",
                                        UploadForwarding.streamedBody(file, filename)
                                )
                                .contentType(fileType);
                    } catch (IOException ex) {
                        throw new IllegalArgumentException("无法读取上传的文档", ex);
                    }
                    return restClient.post()
                            .uri("/api/knowledge/documents")
                            .header("X-Request-Id", requestId)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .body(body.build())
                            .retrieve()
                            .body(KnowledgeDocumentResponse.class);
                }
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
}
