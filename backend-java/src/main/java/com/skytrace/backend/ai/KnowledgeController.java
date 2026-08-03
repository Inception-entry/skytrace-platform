package com.skytrace.backend.ai;

import com.skytrace.backend.ai.client.AiKnowledgeClient;
import com.skytrace.backend.ai.dto.KnowledgeDeleteResponse;
import com.skytrace.backend.ai.dto.KnowledgeDocumentResponse;
import com.skytrace.backend.ai.dto.KnowledgeSearchRequest;
import com.skytrace.backend.ai.dto.KnowledgeSearchResult;
import com.skytrace.backend.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    private final AiKnowledgeClient knowledgeClient;

    public KnowledgeController(AiKnowledgeClient knowledgeClient) {
        this.knowledgeClient = knowledgeClient;
    }

    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeDocumentResponse>> listDocuments() {
        return ApiResponse.ok(knowledgeClient.listDocuments());
    }

    @PostMapping("/documents")
    public ApiResponse<KnowledgeDocumentResponse> upload(
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(knowledgeClient.upload(file));
    }

    @PostMapping("/search")
    public ApiResponse<List<KnowledgeSearchResult>> search(
            @Valid @RequestBody KnowledgeSearchRequest request) {
        return ApiResponse.ok(knowledgeClient.search(request));
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<KnowledgeDeleteResponse> delete(
            @PathVariable String documentId) {
        return ApiResponse.ok(knowledgeClient.delete(documentId));
    }
}
