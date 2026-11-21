package com.docureader.service;

import com.docureader.model.Document;
import com.docureader.model.DocumentChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    @Value("${docureader.rag.chunk-size:500}")
    private int chunkSizeWords;

    @Value("${docureader.rag.chunk-overlap:100}")
    private int chunkOverlapWords;

    public static class RawChunk {
        private final int chunkIndex;
        private final int pageNumber;
        private final String content;
        private final int estimatedTokenCount;

        public RawChunk(int chunkIndex, int pageNumber, String content, int estimatedTokenCount) {
            this.chunkIndex = chunkIndex;
            this.pageNumber = pageNumber;
            this.content = content;
            this.estimatedTokenCount = estimatedTokenCount;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public String getContent() {
            return content;
        }

        public int getEstimatedTokenCount() {
            return estimatedTokenCount;
        }
    }

    public List<DocumentChunk> chunkDocument(Document document, List<TextExtractionService.PageContent> pages) {
        List<DocumentChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (TextExtractionService.PageContent page : pages) {
            String text = page.getText();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            // Split into words while preserving sentence boundaries
            String[] words = text.split("\\s+");
            if (words.length <= chunkSizeWords) {
                // Whole page fits in single chunk
                DocumentChunk chunk = new DocumentChunk(
                        document,
                        chunkIndex++,
                        page.getPageNumber(),
                        text,
                        estimateTokens(words.length)
                );
                chunks.add(chunk);
            } else {
                // Sliding window chunking
                int start = 0;
                while (start < words.length) {
                    int end = Math.min(start + chunkSizeWords, words.length);

                    StringBuilder chunkText = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        chunkText.append(words[i]).append(" ");
                    }

                    DocumentChunk chunk = new DocumentChunk(
                            document,
                            chunkIndex++,
                            page.getPageNumber(),
                            chunkText.toString().trim(),
                            estimateTokens(end - start)
                    );
                    chunks.add(chunk);

                    if (end >= words.length) {
                        break;
                    }
                    start += (chunkSizeWords - chunkOverlapWords);
                }
            }
        }

        return chunks;
    }

    private int estimateTokens(int wordCount) {
        // Typically 1 word is approx 1.3 to 1.4 tokens in English
        return (int) Math.round(wordCount * 1.33);
    }
}
