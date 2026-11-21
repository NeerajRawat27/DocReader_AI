package com.docureader.controller;

import com.docureader.dto.DemoSessionResponse;
import com.docureader.security.UserPrincipal;
import com.docureader.service.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Demo & Guest Sessions", description = "Instant frictionless guest sessions and quota tracking")
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final RateLimiterService rateLimiterService;

    public DemoController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Operation(summary = "Start guest demo session", description = "Generates a temporary guest token with 5 complimentary RAG queries and IP rate limiting.")
    @PostMapping("/start")
    public ResponseEntity<DemoSessionResponse> startDemoSession(jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        DemoSessionResponse response = rateLimiterService.createDemoSession(clientIp);
        return ResponseEntity.ok(response);
    }

    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    @Operation(summary = "Check demo status", description = "Checks whether the current user is a guest demo user and returns query quota details.")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getDemoStatus(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            return ResponseEntity.ok(Map.of(
                    "isDemo", false,
                    "maxQueries", rateLimiterService.getMaxDemoQueries()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "isDemo", userPrincipal.isDemo(),
                "userId", userPrincipal.getId(),
                "username", userPrincipal.getUsername()
        ));
    }
}
