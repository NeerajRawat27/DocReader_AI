package com.docureader.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Arrays;

@Entity
@Table(name = "document_chunks", indexes = {
    @Index(name = "idx_chunk_document", columnList = "document_id")
})
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Lob
    @Column(name = "embedding_data", columnDefinition = "TEXT")
    private String embeddingData;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public DocumentChunk() {
    }

    public DocumentChunk(Document document, Integer chunkIndex, Integer pageNumber, String content, Integer tokenCount) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.pageNumber = pageNumber;
        this.content = content;
        this.tokenCount = tokenCount;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEmbeddingData() {
        return embeddingData;
    }

    public void setEmbeddingData(String embeddingData) {
        this.embeddingData = embeddingData;
    }

    public Integer getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount) {
        this.tokenCount = tokenCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Transient
    public float[] getEmbeddingVector() {
        if (embeddingData == null || embeddingData.trim().isEmpty()) {
            return new float[0];
        }
        String[] parts = embeddingData.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    @Transient
    public void setEmbeddingVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            this.embeddingData = null;
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        this.embeddingData = sb.toString();
    }
}
