package com.docureader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int EMBEDDING_DIMENSION = 1536;

    @Value("${docureader.openai.api-key:}")
    private String openAiApiKey;

    @Value("${docureader.openai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${docureader.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[EMBEDDING_DIMENSION];
        }

        if (isRemoteEmbeddingConfigured()) {
            try {
                return callOpenAiEmbedding(text);
            } catch (Exception ex) {
                log.warn("Remote embedding service unavailable, utilizing internal vectorizer: {}", ex.getMessage());
            }
        }

        return generateDeterministicEmbedding(text);
    }

    private boolean isRemoteEmbeddingConfigured() {
        return openAiApiKey != null && !openAiApiKey.trim().isEmpty() 
                && !openAiApiKey.equalsIgnoreCase("none") 
                && !openAiApiKey.startsWith("gsk_");
    }

    private float[] callOpenAiEmbedding(String text) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey.trim());

        Map<String, Object> body = new HashMap<>();
        body.put("input", text);
        body.put("model", embeddingModel);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/embeddings", requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode embeddingNode = root.path("data").get(0).path("embedding");
            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            return vector;
        } else {
            throw new RuntimeException("Embedding service returned HTTP " + response.getStatusCode());
        }
    }

    /**
     * Generates a 1536-dimensional L2-normalized feature vector.
     * Computes multi-hash token representations for cosine similarity matching.
     */
    public float[] generateDeterministicEmbedding(String text) {
        float[] vector = new float[EMBEDDING_DIMENSION];
        String clean = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        String[] tokens = clean.split("\\s+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            int hash1 = Math.abs(token.hashCode()) % EMBEDDING_DIMENSION;
            int hash2 = Math.abs((token + "_alt").hashCode()) % EMBEDDING_DIMENSION;
            int hash3 = Math.abs((token + "_sub").hashCode()) % EMBEDDING_DIMENSION;

            vector[hash1] += 1.0f;
            vector[hash2] += 0.5f;
            vector[hash3] += 0.25f;
        }

        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += v * v;
        }

        if (sumSquares > 0.0) {
            float norm = (float) Math.sqrt(sumSquares);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }
}
