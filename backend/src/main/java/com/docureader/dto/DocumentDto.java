package com.docureader.dto;

import java.time.LocalDateTime;

public class DocumentDto {
    private Long id;
    private String title;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private Integer pageCount;
    private Integer chunkCount;
    private LocalDateTime createdAt;

    public DocumentDto() {
    }

    public DocumentDto(Long id, String title, String originalFilename, String contentType, Long fileSize, Integer pageCount, Integer chunkCount, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.pageCount = pageCount;
        this.chunkCount = chunkCount;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
