package com.tejesh.timetable.service;

import org.apache.pdfbox.Loader;                        // PDFBox 3.x: replaces PDDocument.load()
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/**
 * <h2>PdfExtractorService – Syllabus PDF Text Extractor</h2>
 *
 * <p>Uses <b>Apache PDFBox 3.x</b> to extract all readable text from an uploaded
 * syllabus or existing timetable PDF. The raw text is then passed to
 * {@code AIBrainService} as additional context so the AI can align the generated
 * timetable with the official curriculum.
 *
 * <h3>What it handles:</h3>
 * <ul>
 *   <li>Multi-page PDFs (entire document is extracted in one call)</li>
 *   <li>Whitespace normalization (collapses multiple blank lines / spaces)</li>
 *   <li>Graceful error handling — never crashes the caller; returns empty string
 *       with a logged warning so the app continues without PDF context</li>
 * </ul>
 */
@Service
public class PdfExtractorService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractorService.class);

    /**
     * Extracts all text content from a PDF file using Apache PDFBox.
     *
     * <p>Uses {@code try-with-resources} to guarantee the {@link PDDocument}
     * is closed even if an exception occurs, preventing file-handle leaks.
     *
     * @param pdfFile the PDF file to parse (must exist and be readable)
     * @return the full extracted text string; returns an empty string {@code ""}
     *         if the file is null, does not exist, or cannot be parsed
     */
    public String extractTextFromPdf(File pdfFile) {

        // ── Guard: null or missing file ───────────────────────────────────────
        if (pdfFile == null) {
            log.warn("PdfExtractorService: received null file — returning empty string.");
            return "";
        }
        if (!pdfFile.exists()) {
            log.warn("PdfExtractorService: file does not exist at path '{}' — returning empty string.",
                    pdfFile.getAbsolutePath());
            return "";
        }
        if (!pdfFile.canRead()) {
            log.warn("PdfExtractorService: file is not readable '{}' — returning empty string.",
                    pdfFile.getAbsolutePath());
            return "";
        }

        log.info("PdfExtractorService: Extracting text from '{}'  ({} bytes)",
                pdfFile.getName(), pdfFile.length());

        // ── Core extraction ───────────────────────────────────────────────────
        try (PDDocument document = Loader.loadPDF(pdfFile)) {  // PDFBox 3.x API

            int pageCount = document.getNumberOfPages();
            log.debug("PdfExtractorService: PDF has {} page(s).", pageCount);

            if (pageCount == 0) {
                log.warn("PdfExtractorService: PDF has 0 pages — returning empty string.");
                return "";
            }

            // PDFTextStripper reads all pages by default
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);  // maintain reading order (top-to-bottom, left-to-right)
            stripper.setStartPage(1);
            stripper.setEndPage(pageCount);

            String rawText = stripper.getText(document);

            // ── Cleanup ───────────────────────────────────────────────────────
            String cleanedText = normalizeWhitespace(rawText);

            log.info("PdfExtractorService: Extracted {} chars from {} page(s) of '{}'.",
                    cleanedText.length(), pageCount, pdfFile.getName());

            return cleanedText;

        } catch (IOException e) {
            log.error("PdfExtractorService: Failed to parse PDF '{}'. Reason: {}",
                    pdfFile.getName(), e.getMessage(), e);
            return ""; // Graceful degradation — AI brain continues without PDF context
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Collapses multiple consecutive blank lines into a single blank line
     * and trims leading/trailing whitespace from each line.
     *
     * <p>PDFBox occasionally extracts large amounts of whitespace from PDFs
     * with complex layouts (tables, columns). This normalization prevents the
     * Gemini prompt from being padded with useless whitespace characters.
     *
     * @param raw the raw text returned by PDFTextStripper
     * @return cleaned, compacted text
     */
    private String normalizeWhitespace(String raw) {
        if (raw == null || raw.isBlank()) return "";

        // Split into lines, trim each, then rejoin
        String[] lines = raw.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        int consecutiveBlankLines = 0;

        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                consecutiveBlankLines++;
                // Allow at most one consecutive blank line (paragraph separator)
                if (consecutiveBlankLines <= 1) {
                    sb.append('\n');
                }
            } else {
                consecutiveBlankLines = 0;
                sb.append(trimmed).append('\n');
            }
        }

        // Collapse multiple internal spaces (e.g., table-cell padding)
        return sb.toString()
                 .replaceAll("[ \\t]{2,}", " ")
                 .strip();
    }
}
