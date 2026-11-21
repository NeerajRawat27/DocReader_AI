package com.docureader.service;

import com.docureader.model.Document;
import com.docureader.model.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingService();
        ReflectionTestUtils.setField(chunkingService, "chunkSizeWords", 10);
        ReflectionTestUtils.setField(chunkingService, "chunkOverlapWords", 2);
    }

    @Test
    void testChunkingSmallText() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setTitle("Test Doc");

        List<TextExtractionService.PageContent> pages = List.of(
                new TextExtractionService.PageContent(1, "Hello world, this is a short test page.")
        );

        List<DocumentChunk> chunks = chunkingService.chunkDocument(doc, pages);
        assertEquals(1, chunks.size());
        assertEquals(1, chunks.get(0).getPageNumber());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertTrue(chunks.get(0).getContent().contains("Hello world"));
    }

    @Test
    void testChunkingSlidingWindowWithOverlap() {
        Document doc = new Document();
        doc.setId(1L);
        doc.setTitle("Long Doc");

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            sb.append("word").append(i).append(" ");
        }

        List<TextExtractionService.PageContent> pages = List.of(
                new TextExtractionService.PageContent(1, sb.toString())
        );

        List<DocumentChunk> chunks = chunkingService.chunkDocument(doc, pages);
        assertTrue(chunks.size() > 1, "Should create multiple chunks for 25 words with chunkSize=10");

        // Verify overlapping words
        DocumentChunk firstChunk = chunks.get(0);
        DocumentChunk secondChunk = chunks.get(1);

        assertTrue(firstChunk.getContent().contains("word10"));
        assertTrue(secondChunk.getContent().contains("word9") || secondChunk.getContent().contains("word10"));
    }
}
