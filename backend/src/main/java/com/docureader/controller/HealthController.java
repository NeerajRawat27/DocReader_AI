package com.docureader.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "System Health", description = "Liveness probe and platform status")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Operation(summary = "System health check", description = "Returns active platform status, engine name, and timestamp.")
    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "DocReader.ai Document Intelligence Engine",
                "timestamp", LocalDateTime.now(),
                "version", "1.0.0"
        ));
    }
}
