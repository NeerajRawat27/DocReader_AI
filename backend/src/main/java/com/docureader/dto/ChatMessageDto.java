package com.docureader.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ChatMessageDto {
    private Long id;
    private String role;
    private String content;
    private List<SourceCitationDto> citations;
    private LocalDateTime createdAt;

    public ChatMessageDto() {
    }

    public ChatMessageDto(Long id, String role, String content, List<SourceCitationDto> citations, LocalDateTime createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.citations = citations;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<SourceCitationDto> getCitations() {
        return citations;
    }

    public void setCitations(List<SourceCitationDto> citations) {
        this.citations = citations;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
