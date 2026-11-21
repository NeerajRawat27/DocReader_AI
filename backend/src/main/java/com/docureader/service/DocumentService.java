package com.docureader.service;

import com.docureader.dto.DocumentDto;
import com.docureader.exception.ResourceNotFoundException;
import com.docureader.exception.UnauthorizedException;
import com.docureader.model.Document;
import com.docureader.model.DocumentChunk;
import com.docureader.model.User;
import com.docureader.repository.DocumentChunkRepository;
import com.docureader.repository.DocumentRepository;
import com.docureader.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final UserRepository userRepository;
    private final TextExtractionService textExtractionService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunkRepository chunkRepository,
                           UserRepository userRepository,
                           TextExtractionService textExtractionService,
                           ChunkingService chunkingService,
                           EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.userRepository = userRepository;
        this.textExtractionService = textExtractionService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public DocumentDto ingestDocument(Long userId, MultipartFile file, String customTitle) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty document.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.trim().isEmpty()) {
            rawFilename = "document.txt";
        }

        // Sanitize filename to prevent path traversal or null byte injection
        String filename = Paths.get(rawFilename).getFileName().toString().replaceAll("[\\r\\n\\u0000]", "");
        String lowerName = filename.toLowerCase();

        // Strict extension validation
        if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".docx") && !lowerName.endsWith(".txt") && !lowerName.endsWith(".md")) {
            throw new IllegalArgumentException("Unsupported file type (" + filename + "). Only PDF, DOCX, and TXT/MD files are supported.");
        }

        String title = (customTitle != null && !customTitle.trim().isEmpty()) ? customTitle.trim() : filename;

        log.info("Starting ingestion of file '{}' (size: {} bytes) for user '{}'", filename, file.getSize(), user.getUsername());

        // 1. Extract text and pages
        TextExtractionService.ExtractionResult extractionResult = textExtractionService.extractText(file);

        // 2. Persist Document entity
        Document document = new Document(
                title,
                filename,
                file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                file.getSize(),
                extractionResult.getTotalPages(),
                user
        );
        document = documentRepository.save(document);

        // 3. Chunk text into semantic windows
        List<DocumentChunk> chunks = chunkingService.chunkDocument(document, extractionResult.getPages());

        // 4. Compute vector embeddings for all chunks
        for (DocumentChunk chunk : chunks) {
            float[] vector = embeddingService.generateEmbedding(chunk.getContent());
            chunk.setEmbeddingVector(vector);
        }

        chunkRepository.saveAll(chunks);
        document.setChunkCount(chunks.size());
        document = documentRepository.save(document);

        log.info("Successfully ingested document '{}' with {} chunks and embeddings", title, chunks.size());

        return mapToDto(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> getUserDocuments(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        return documentRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DocumentDto getDocumentById(Long userId, Long documentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Document document = documentRepository.findByIdAndUser(documentId, user)
                .orElseThrow(() -> new UnauthorizedException("Document not found or access denied"));

        return mapToDto(document);
    }

    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Document document = documentRepository.findByIdAndUser(documentId, user)
                .orElseThrow(() -> new UnauthorizedException("Document not found or access denied"));

        chunkRepository.deleteByDocument(document);
        documentRepository.delete(document);
        log.info("Deleted document id {} for user {}", documentId, user.getUsername());
    }

    private DocumentDto mapToDto(Document doc) {
        return new DocumentDto(
                doc.getId(),
                doc.getTitle(),
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getFileSize(),
                doc.getPageCount(),
                doc.getChunkCount(),
                doc.getCreatedAt()
        );
    }
}
