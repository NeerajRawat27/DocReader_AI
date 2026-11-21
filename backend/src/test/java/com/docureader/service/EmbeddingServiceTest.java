package com.docureader.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EmbeddingServiceTest {

    private final EmbeddingService embeddingService = new EmbeddingService();

    @Test
    void testDeterministicEmbeddingDimension() {
        float[] vector = embeddingService.generateDeterministicEmbedding("Knowledge graph and RAG retrieval");
        assertEquals(1536, vector.length);

        // Check L2 normalization (sum of squares ~ 1.0)
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += v * v;
        }
        assertEquals(1.0, sumSquares, 0.001);
    }

    @Test
    void testSimilarTextHasHigherCosineSimilarity() {
        VectorStoreService vectorStore = new VectorStoreService(null, null);

        float[] v1 = embeddingService.generateDeterministicEmbedding("machine learning natural language processing");
        float[] v2 = embeddingService.generateDeterministicEmbedding("machine learning language models");
        float[] v3 = embeddingService.generateDeterministicEmbedding("recipe for baking chocolate cookies");

        double simRelated = vectorStore.computeCosineSimilarity(v1, v2);
        double simUnrelated = vectorStore.computeCosineSimilarity(v1, v3);

        assertTrue(simRelated > simUnrelated, "Related technical terms should have higher similarity than cookie recipes");
    }
}
