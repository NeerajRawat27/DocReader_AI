package com.docureader.controller;

import com.docureader.dto.DocumentDto;
import com.docureader.exception.UnauthorizedException;
import com.docureader.security.UserPrincipal;
import com.docureader.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "Documents", description = "Upload, parse, index, and manage documents and semantic vectors")
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "Upload and index document", description = "Uploads a PDF, DOCX, or TXT/MD document, performs text extraction, chunking, and vector indexing.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> uploadDocument(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Document file (PDF, DOCX, TXT, MD up to 25MB)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional custom document title") @RequestParam(value = "title", required = false) String title) throws Exception {

        if (userPrincipal == null) {
            throw new UnauthorizedException("Session expired or unauthorized. Please re-authenticate.");
        }

        DocumentDto documentDto = documentService.ingestDocument(userPrincipal.getId(), file, title);
        return ResponseEntity.ok(documentDto);
    }

    @Operation(summary = "Get user documents", description = "Retrieves all indexed documents belonging to the authenticated user.")
    @GetMapping
    public ResponseEntity<List<DocumentDto>> getUserDocuments(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        if (userPrincipal == null) {
            throw new UnauthorizedException("Session expired or unauthorized.");
        }

        List<DocumentDto> docs = documentService.getUserDocuments(userPrincipal.getId());
        return ResponseEntity.ok(docs);
    }

    @Operation(summary = "Get document metadata", description = "Retrieves metadata and page count for a specific document by ID.")
    @GetMapping("/{id}")
    public ResponseEntity<DocumentDto> getDocument(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Document ID") @PathVariable Long id) {

        if (userPrincipal == null) {
            throw new UnauthorizedException("Session expired or unauthorized.");
        }

        DocumentDto doc = documentService.getDocumentById(userPrincipal.getId(), id);
        return ResponseEntity.ok(doc);
    }

    @Operation(summary = "Delete document", description = "Deletes a document and purges all of its vector chunk embeddings.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Document ID") @PathVariable Long id) {

        if (userPrincipal == null) {
            throw new UnauthorizedException("Session expired or unauthorized.");
        }

        documentService.deleteDocument(userPrincipal.getId(), id);
        return ResponseEntity.ok(Map.of("message", "Document deleted successfully", "id", id.toString()));
    }
}
