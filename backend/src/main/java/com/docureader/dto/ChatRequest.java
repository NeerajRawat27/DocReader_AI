package com.docureader.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class ChatRequest {
    private Long sessionId;

    @NotBlank(message = "Question cannot be blank")
    private String question;

    private List<Long> documentIds;

    public ChatRequest() {
    }

    public ChatRequest(Long sessionId, String question, List<Long> documentIds) {
        this.sessionId = sessionId;
        this.question = question;
        this.documentIds = documentIds;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<Long> getDocumentIds() {
        return documentIds;
    }

    public void setDocumentIds(List<Long> documentIds) {
        this.documentIds = documentIds;
    }
}
