package com.docureader.dto;

public class SourceCitationDto {
    private Long documentId;
    private String documentTitle;
    private Integer pageNumber;
    private Integer chunkIndex;
    private String excerpt;
    private double relevanceScore;

    public SourceCitationDto() {
    }

    public SourceCitationDto(Long documentId, String documentTitle, Integer pageNumber, Integer chunkIndex, String excerpt, double relevanceScore) {
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.pageNumber = pageNumber;
        this.chunkIndex = chunkIndex;
        this.excerpt = excerpt;
        this.relevanceScore = relevanceScore;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}
