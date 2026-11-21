package com.docureader.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    public static class PageContent {
        private final int pageNumber;
        private final String text;

        public PageContent(int pageNumber, String text) {
            this.pageNumber = pageNumber;
            this.text = text;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        public String getText() {
            return text;
        }
    }

    public static class ExtractionResult {
        private final List<PageContent> pages;
        private final int totalPages;

        public ExtractionResult(List<PageContent> pages, int totalPages) {
            this.pages = pages;
            this.totalPages = totalPages;
        }

        public List<PageContent> getPages() {
            return pages;
        }

        public int getTotalPages() {
            return totalPages;
        }
    }

    private static final int MAX_PAGES = 150;
    private static final int MAX_TOTAL_CHARS = 1_000_000; // 1 MB text ceiling

    static {
        // Guard against Zip Bomb attacks in Office OpenXML (.docx) documents
        try {
            org.apache.poi.openxml4j.util.ZipSecureFile.setMinInflateRatio(0.005);
            org.apache.poi.openxml4j.util.ZipSecureFile.setMaxEntrySize(50_000_000L); // 50MB
        } catch (Throwable t) {
            log.warn("Could not configure ZipSecureFile limits: {}", t.getMessage());
        }
    }

    public ExtractionResult extractText(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            filename = "document.txt";
        }
        String lowerName = filename.toLowerCase();

        if (lowerName.endsWith(".pdf")) {
            return extractFromPdf(file.getInputStream());
        } else if (lowerName.endsWith(".docx")) {
            return extractFromDocx(file.getInputStream());
        } else {
            return extractFromPlainText(file.getInputStream());
        }
    }

    public ExtractionResult extractFromPdf(InputStream inputStream) throws Exception {
        List<PageContent> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(inputStream)) {
            int numberOfPages = document.getNumberOfPages();
            int pagesToProcess = Math.min(numberOfPages, MAX_PAGES);
            PDFTextStripper stripper = new PDFTextStripper();

            int totalChars = 0;
            for (int p = 1; p <= pagesToProcess; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pageText = stripper.getText(document).trim();
                if (!pageText.isEmpty()) {
                    if (totalChars + pageText.length() > MAX_TOTAL_CHARS) {
                        int remaining = MAX_TOTAL_CHARS - totalChars;
                        if (remaining > 0) {
                            pages.add(new PageContent(p, pageText.substring(0, remaining) + "\n[Content truncated for security and size limits]"));
                        }
                        break;
                    }
                    totalChars += pageText.length();
                    pages.add(new PageContent(p, pageText));
                }
            }

            if (pages.isEmpty()) {
                pages.add(new PageContent(1, "[Document had no extractable text]"));
            }

            return new ExtractionResult(pages, numberOfPages);
        }
    }

    public ExtractionResult extractFromDocx(InputStream inputStream) throws Exception {
        List<PageContent> pages = new ArrayList<>();
        try (XWPFDocument docx = new XWPFDocument(inputStream)) {
            StringBuilder currentText = new StringBuilder();
            int pageIndex = 1;
            int wordCount = 0;
            int totalChars = 0;

            for (XWPFParagraph p : docx.getParagraphs()) {
                String pText = p.getText().trim();
                if (!pText.isEmpty()) {
                    currentText.append(pText).append("\n\n");
                    wordCount += pText.split("\\s+").length;
                    totalChars += pText.length();

                    // Approximate standard page size (~400 words per page in DOCX)
                    if (wordCount >= 400) {
                        pages.add(new PageContent(pageIndex++, currentText.toString().trim()));
                        currentText.setLength(0);
                        wordCount = 0;

                        if (pages.size() >= MAX_PAGES || totalChars >= MAX_TOTAL_CHARS) {
                            log.info("Reached maximum extraction limits for DOCX (pages: {}, chars: {})", pages.size(), totalChars);
                            break;
                        }
                    }
                }
            }

            if (currentText.length() > 0 && pages.size() < MAX_PAGES && totalChars < MAX_TOTAL_CHARS) {
                pages.add(new PageContent(pageIndex, currentText.toString().trim()));
            }

            if (pages.isEmpty()) {
                pages.add(new PageContent(1, "[Empty Word Document]"));
            }

            return new ExtractionResult(pages, pages.size());
        }
    }

    public ExtractionResult extractFromPlainText(InputStream inputStream) throws Exception {
        byte[] bytes = inputStream.readNBytes(MAX_TOTAL_CHARS);
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        List<PageContent> pages = new ArrayList<>();
        pages.add(new PageContent(1, text));
        return new ExtractionResult(pages, 1);
    }
}
