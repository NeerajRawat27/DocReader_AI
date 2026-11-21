package com.docureader.service;

import com.docureader.dto.SourceCitationDto;
import com.docureader.model.DocumentChunk;
import com.docureader.repository.DocumentChunkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VectorStoreService {

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;

    @Value("${docureader.rag.top-k:4}")
    private int defaultTopK;

    @Value("${docureader.rag.min-similarity:0.15}")
    private double minSimilarityThreshold;

    public VectorStoreService(DocumentChunkRepository chunkRepository, EmbeddingService embeddingService) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
    }

    public static class ScoredChunk {
        private final DocumentChunk chunk;
        private final double score;

        public ScoredChunk(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }

        public DocumentChunk getChunk() {
            return chunk;
        }

        public double getScore() {
            return score;
        }
    }

    @Transactional(readOnly = true)
    public List<ScoredChunk> searchSimilarChunks(Long userId, List<Long> filterDocumentIds, String query, Integer topK) {
        int k = (topK != null && topK > 0) ? topK : defaultTopK;
        float[] queryVector = embeddingService.generateEmbedding(query);

        List<DocumentChunk> chunks;
        if (filterDocumentIds != null && !filterDocumentIds.isEmpty()) {
            chunks = chunkRepository.findByDocumentIdsAndUserId(filterDocumentIds, userId);
        } else {
            chunks = chunkRepository.findAllByUserId(userId);
        }

        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<ScoredChunk> allScored = new ArrayList<>();
        List<ScoredChunk> passedThreshold = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            float[] chunkVector = chunk.getEmbeddingVector();
            double similarity = computeCosineSimilarity(queryVector, chunkVector);
            double lexicalBoost = computeLexicalOverlap(query, chunk.getContent());
            double combinedScore = (similarity * 0.6) + (lexicalBoost * 0.4);

            ScoredChunk sc = new ScoredChunk(chunk, Math.max(similarity, combinedScore));
            allScored.add(sc);
            if (combinedScore >= minSimilarityThreshold || similarity >= minSimilarityThreshold) {
                passedThreshold.add(sc);
            }
        }

        List<ScoredChunk> candidates = passedThreshold.isEmpty() ? allScored : passedThreshold;
        candidates.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        return candidates.stream().limit(k).collect(Collectors.toList());
    }

    private double computeLexicalOverlap(String query, String content) {
        if (query == null || content == null) return 0.0;
        String[] qTokens = query.toLowerCase().split("\\W+");
        String contentLower = content.toLowerCase();
        int matches = 0;
        int meaningfulWords = 0;
        for (String q : qTokens) {
            if (q.length() <= 2) continue;
            meaningfulWords++;
            if (contentLower.contains(q)) {
                matches++;
            }
        }
        return meaningfulWords > 0 ? (double) matches / meaningfulWords : 0.0;
    }

    public double computeCosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA == null || vecB == null || vecA.length == 0 || vecB.length == 0) {
            return 0.0;
        }
        int minLen = Math.min(vecA.length, vecB.length);
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < minLen; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
