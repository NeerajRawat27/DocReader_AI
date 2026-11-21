package com.docureader.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.docureader.dto.*;
import com.docureader.exception.ResourceNotFoundException;
import com.docureader.exception.UnauthorizedException;
import com.docureader.model.ChatMessage;
import com.docureader.model.ChatSession;
import com.docureader.model.User;
import com.docureader.repository.ChatMessageRepository;
import com.docureader.repository.ChatSessionRepository;
import com.docureader.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RagService ragService;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       UserRepository userRepository,
                       RagService ragService,
                       RateLimiterService rateLimiterService) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.ragService = ragService;
        this.rateLimiterService = rateLimiterService;
    }

    @Transactional
    public SseEmitter streamChatResponse(Long userId, ChatRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Enforce demo rate limits
        rateLimiterService.recordAndCheckRateLimit(user);

        // Get or create session
        ChatSession session;
        if (request.getSessionId() != null) {
            session = sessionRepository.findByIdAndUser(request.getSessionId(), user)
                    .orElseThrow(() -> new UnauthorizedException("Session not found or unauthorized"));
        } else {
            String title = request.getQuestion().trim();
            if (title.length() > 40) {
                title = title.substring(0, 40) + "...";
            }
            session = new ChatSession(title, user, user.isDemo());
            session = sessionRepository.save(session);
        }

        // Persist User message
        ChatMessage userMsg = new ChatMessage(session, "USER", request.getQuestion(), null);
        messageRepository.save(userMsg);

        // Retrieve RAG Context and Grounding
        RagService.RagContextResult contextResult =
                ragService.retrieveContext(userId, request.getDocumentIds(), request.getQuestion());

        SseEmitter emitter = new SseEmitter(120000L); // 2 minutes timeout
        StringBuilder completeAnswer = new StringBuilder();
        Long finalSessionId = session.getId();

        // Send initial metadata event (Session ID & remaining quota)
        try {
            Map<String, Object> initData = new HashMap<>();
            initData.put("sessionId", finalSessionId);
            initData.put("remainingQueries", rateLimiterService.getRemainingQueries(user));
            emitter.send(SseEmitter.event().name("init").data(objectMapper.writeValueAsString(initData)));
        } catch (IOException e) {
            log.error("Failed to send init event", e);
        }

        ragService.streamCompletion(
                request.getQuestion(),
                contextResult,
                token -> {
                    try {
                        completeAnswer.append(token);
                        emitter.send(SseEmitter.event().name("token").data(objectMapper.writeValueAsString(token)));
                    } catch (IOException e) {
                        log.error("Failed to stream token via SSE", e);
                    }
                },
                () -> {
                    try {
                        // Send citations event
                        String citationsJson = objectMapper.writeValueAsString(contextResult.getCitations());
                        emitter.send(SseEmitter.event().name("citations").data(citationsJson));

                        // Persist Assistant response
                        persistAssistantMessage(finalSessionId, completeAnswer.toString(), citationsJson);

                        // Complete SSE
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Error finalizing SSE stream", e);
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("Error during streaming generation", error);
                    emitter.completeWithError(error);
                }
        );

        return emitter;
    }

    @Transactional
    public void persistAssistantMessage(Long sessionId, String answer, String citationsJson) {
        ChatSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session != null) {
            ChatMessage assistantMsg = new ChatMessage(session, "ASSISTANT", answer, citationsJson);
            messageRepository.save(assistantMsg);
        }
    }

    @Transactional(readOnly = true)
    public List<ChatSessionDto> getUserSessions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return sessionRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::mapToSessionDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ChatSessionDto getSessionDetails(Long userId, Long sessionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ChatSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new UnauthorizedException("Session not found or access denied"));

        return mapToSessionDtoWithMessages(session);
    }

    private ChatSessionDto mapToSessionDto(ChatSession session) {
        return new ChatSessionDto(
                session.getId(),
                session.getTitle(),
                session.isDemo(),
                session.getCreatedAt(),
                Collections.emptyList()
        );
    }

    private ChatSessionDto mapToSessionDtoWithMessages(ChatSession session) {
        List<ChatMessageDto> msgDtos = session.getMessages().stream()
                .map(msg -> {
                    List<SourceCitationDto> citations = Collections.emptyList();
                    if (msg.getSourceCitationsJson() != null && !msg.getSourceCitationsJson().isEmpty()) {
                        try {
                            citations = objectMapper.readValue(
                                    msg.getSourceCitationsJson(),
                                    new TypeReference<List<SourceCitationDto>>() {}
                            );
                        } catch (Exception ignored) {
                        }
                    }
                    return new ChatMessageDto(
                            msg.getId(),
                            msg.getRole(),
                            msg.getContent(),
                            citations,
                            msg.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());

        return new ChatSessionDto(
                session.getId(),
                session.getTitle(),
                session.isDemo(),
                session.getCreatedAt(),
                msgDtos
        );
    }
}
