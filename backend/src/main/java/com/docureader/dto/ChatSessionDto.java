package com.docureader.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ChatSessionDto {
    private Long id;
    private String title;
    private boolean isDemo;
    private LocalDateTime createdAt;
    private List<ChatMessageDto> messages;

    public ChatSessionDto() {
    }

    public ChatSessionDto(Long id, String title, boolean isDemo, LocalDateTime createdAt, List<ChatMessageDto> messages) {
        this.id = id;
        this.title = title;
        this.isDemo = isDemo;
        this.createdAt = createdAt;
        this.messages = messages;
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

    public boolean isDemo() {
        return isDemo;
    }

    public void setDemo(boolean demo) {
        isDemo = demo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ChatMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessageDto> messages) {
        this.messages = messages;
    }
}
