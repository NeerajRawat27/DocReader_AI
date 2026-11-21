package com.docureader.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VectorStoreServiceTest {

    private final VectorStoreService vectorStoreService = new VectorStoreService(null, null);

    @Test
    void testCosineSimilarityIdenticalVectors() {
        float[] a = new float[]{1.0f, 0.0f, 0.0f};
        float[] b = new float[]{1.0f, 0.0f, 0.0f};

        double similarity = vectorStoreService.computeCosineSimilarity(a, b);
        assertEquals(1.0, similarity, 0.0001);
    }

    @Test
    void testCosineSimilarityOrthogonalVectors() {
        float[] a = new float[]{1.0f, 0.0f, 0.0f};
        float[] b = new float[]{0.0f, 1.0f, 0.0f};

        double similarity = vectorStoreService.computeCosineSimilarity(a, b);
        assertEquals(0.0, similarity, 0.0001);
    }

    @Test
    void testCosineSimilarityNormalizedVectors() {
        float[] a = new float[]{0.6f, 0.8f};
        float[] b = new float[]{0.6f, 0.8f};

        double similarity = vectorStoreService.computeCosineSimilarity(a, b);
        assertEquals(1.0, similarity, 0.0001);
    }

    @Test
    void testCosineSimilarityEmptyVectors() {
        double similarity = vectorStoreService.computeCosineSimilarity(new float[0], new float[0]);
        assertEquals(0.0, similarity, 0.0001);
    }
}
