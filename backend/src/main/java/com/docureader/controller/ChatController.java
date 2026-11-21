package com.docureader.controller;

import com.docureader.dto.ChatRequest;
import com.docureader.dto.ChatSessionDto;
import com.docureader.security.UserPrincipal;
import com.docureader.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "Chat & RAG Inference", description = "Server-Sent Events streaming chat and conversational history")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "Stream RAG AI response", description = "Streams tokens in real-time via Server-Sent Events (SSE), performing vector retrieval and LLM synthesis.")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody ChatRequest request) {

        return chatService.streamChatResponse(userPrincipal.getId(), request);
    }

    @Operation(summary = "Get user chat sessions", description = "Retrieves all chat sessions and message histories for the authenticated user.")
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionDto>> getSessions(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        List<ChatSessionDto> sessions = chatService.getUserSessions(userPrincipal.getId());
        return ResponseEntity.ok(sessions);
    }

    @Operation(summary = "Get chat session by ID", description = "Retrieves a specific chat session with its full message history and citations.")
    @GetMapping("/sessions/{id}")
    public ResponseEntity<ChatSessionDto> getSession(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Parameter(description = "Chat Session ID") @PathVariable Long id) {

        ChatSessionDto session = chatService.getSessionDetails(userPrincipal.getId(), id);
        return ResponseEntity.ok(session);
    }
}
