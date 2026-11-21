package com.docureader.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;

    @Column(nullable = false, length = 20)
    private String role; // "USER" or "ASSISTANT"

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Lob
    @Column(name = "source_citations", columnDefinition = "TEXT")
    private String sourceCitationsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ChatMessage() {
    }

    public ChatMessage(ChatSession chatSession, String role, String content, String sourceCitationsJson) {
        this.chatSession = chatSession;
        this.role = role;
        this.content = content;
        this.sourceCitationsJson = sourceCitationsJson;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ChatSession getChatSession() {
        return chatSession;
    }

    public void setChatSession(ChatSession chatSession) {
        this.chatSession = chatSession;
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

    public String getSourceCitationsJson() {
        return sourceCitationsJson;
    }

    public void setSourceCitationsJson(String sourceCitationsJson) {
        this.sourceCitationsJson = sourceCitationsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
