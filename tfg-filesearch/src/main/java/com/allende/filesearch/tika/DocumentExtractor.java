package com.allende.filesearch.tika;

import com.allende.filesearch.model.Document;
import com.allende.filesearch.utils.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;

import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Extracts content and metadata from files using Apache Tika.
 * COMPLETELY REWRITTEN for robust content extraction with fallbacks.
 */
public class DocumentExtractor {
    private static final Logger logger = LoggerFactory.getLogger(DocumentExtractor.class);
    private final Tika tika;
    private final Parser parser;
    private final List<String> supportedExtensions;

    // Use Integer.MAX_VALUE to avoid truncation - Tika will handle memory
    // internally
    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024; // 100 MB
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    public DocumentExtractor(List<String> supportedExtensions) {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
        this.supportedExtensions = supportedExtensions;

        // Configure PDF Parser
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true); // Enable for OCR if Tesseract is available
        pdfConfig.setSortByPosition(true);

        logger.info("DocumentExtractor initialized with supported extensions: {}", supportedExtensions);
    }

    public DocumentExtractor() {
        this(List.of("txt", "pdf", "docx", "doc", "html", "htm", "xml", "rtf", "odt",
                "md", "json", "csv", "pptx", "ppt", "xlsx", "xls"));
    }

    /**
     * Extract document content and metadata from a file.
     * Implements multiple fallback strategies for robust extraction.
     *
     * @param filePath Path to the file
     * @return Document object with extracted content and metadata
     * @throws IOException if file cannot be read
     */
    public Document extractDocument(Path filePath) throws IOException {
        long startTime = System.currentTimeMillis();
        logger.debug("Extracting document: {}", filePath);

        Document doc = new Document();
        doc.setPath(filePath.toString());
        doc.setFilename(filePath.getFileName().toString());
        doc.setExtension(FileUtils.getExtension(doc.getFilename()));

        // Get file attributes
        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        doc.setSize(attrs.size());
        doc.setCreatedAt(attrs.creationTime().toInstant());
        doc.setModifiedAt(attrs.lastModifiedTime().toInstant());

        // Skip files that are too large
        if (attrs.size() > MAX_FILE_SIZE_BYTES) {
            logger.warn("File too large ({}MB), skipping content extraction: {}",
                    attrs.size() / (1024 * 1024), filePath);
            doc.setContent("");
            doc.setLastIndexedAt(Instant.now());
            return doc;
        }

        // Skip empty files
        if (attrs.size() == 0) {
            logger.warn("File is empty (0 bytes), skipping: {}", filePath);
            doc.setContent("");
            doc.setLastIndexedAt(Instant.now());
            return doc;
        }

        // Calculate checksum
        try {
            String checksum = FileUtils.calculateSHA256(filePath);
            doc.setChecksumSha256(checksum);
        } catch (IOException e) {
            logger.warn("Could not calculate checksum for {}: {}", filePath, e.getMessage());
        }

        // Extract content with Tika (with fallbacks)
        String extractedContent = extractContentWithFallbacks(filePath, doc.getExtension());

        // Normalize content
        extractedContent = normalizeContent(extractedContent);

        doc.setContent(extractedContent);
        doc.setLastIndexedAt(Instant.now());

        // Detect language if possible
        doc.setLanguage(detectLanguageFromContent(extractedContent));

        long duration = System.currentTimeMillis() - startTime;

        if (extractedContent == null || extractedContent.isEmpty()) {
            logger.warn("No content extracted from: {} ({}ms)",
                    filePath.getFileName(), duration);
        } else {
            logger.info("Successfully extracted: {} → {} chars ({} bytes, {}ms)",
                    filePath.getFileName(), extractedContent.length(), attrs.size(), duration);
        }

        return doc;
    }

    /**
     * Extract content with multiple fallback strategies.
     */
    private String extractContentWithFallbacks(Path filePath, String extension) {
        String content = "";

        // Strategy 1: Try Tika with full parser
        try {
            content = extractWithTika(filePath);
            if (isValidContent(content)) {
                return content;
            }
            logger.debug("Tika returned empty/invalid content, trying fallbacks...");
        } catch (Exception e) {
            logger.warn("Tika extraction failed for {}: {}, trying fallbacks...",
                    filePath.getFileName(), e.getMessage());
        }

        // Strategy 2: For text-based formats, try direct reading
        if (isTextBasedFormat(extension)) {
            try {
                content = readAsPlainText(filePath);
                if (isValidContent(content)) {
                    logger.info("Fallback: Read as plain text successfully");
                    return content;
                }
            } catch (Exception e) {
                logger.debug("Plain text reading failed: {}", e.getMessage());
            }
        }

        // Strategy 3: PDFBox Direct Fallback (Specific for PDF)
        if ("pdf".equalsIgnoreCase(extension)) {
            try {
                content = extractWithPDFBox(filePath);
                if (isValidContent(content)) {
                    logger.info("Fallback: PDFBox extraction successfully");
                    return content;
                }
            } catch (Exception e) {
                logger.debug("PDFBox extraction failed: {}", e.getMessage());
            }
        }

        // Strategy 4: Try simple Tika.parseToString (simpler API)
        try {
            content = tika.parseToString(filePath.toFile());
            if (isValidContent(content)) {
                logger.info("Fallback: Tika.parseToString succeeded");
                return content;
            }
        } catch (Exception e) {
            logger.debug("Tika.parseToString failed: {}", e.getMessage());
        }

        // All strategies failed
        logger.error("All extraction strategies failed for: {}", filePath.getFileName());
        return "";
    }

    private boolean isValidContent(String content) {
        return content != null && !content.trim().isEmpty() && content.trim().length() > 5;
    }

    /**
     * Extract content using Tika's AutoDetectParser (main strategy).
     */
    private String extractWithTika(Path filePath) throws IOException, SAXException, TikaException {
        Metadata metadata = new Metadata();
        // Use -1 for unlimited content (Tika handles this properly)
        BodyContentHandler handler = new BodyContentHandler(-1);
        ParseContext context = new ParseContext();

        // Add PDF configuration to context
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);
        pdfConfig.setSortByPosition(true);
        context.set(PDFParserConfig.class, pdfConfig);

        // CRITICAL: Use BufferedInputStream to ensure stream can be reset if needed
        try (FileInputStream fileStream = new FileInputStream(filePath.toFile());
                BufferedInputStream bufferedStream = new BufferedInputStream(fileStream)) {

            logger.debug("Parsing with Tika: {} ({} bytes)",
                    filePath.getFileName(), Files.size(filePath));

            // Parse the document
            parser.parse(bufferedStream, handler, metadata, context);

            // Get the extracted text
            String content = handler.toString();
            return content;
        }
    }

    /**
     * Extract PDF content directly using PDFBox (Fallback).
     */
    private String extractWithPDFBox(Path filePath) throws IOException {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            if (document.isEncrypted()) {
                logger.warn("PDF is encrypted: {}", filePath);
                return "";
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * Fallback: Read file as plain text (for TXT, MD, JSON, etc.).
     */
    private String readAsPlainText(Path filePath) throws IOException {
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * Normalize extracted content.
     */
    private String normalizeContent(String content) {
        if (content == null)
            return "";

        // Remove control characters (null bytes, etc)
        String normalized = CONTROL_CHARS.matcher(content).replaceAll("");

        // Normalize whitespace (collapse multiple spaces/newlines)
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized.trim();
    }

    /**
     * Check if file format is text-based (can be read directly).
     */
    private boolean isTextBasedFormat(String extension) {
        if (extension == null)
            return false;
        String ext = extension.toLowerCase();
        return ext.equals("txt") || ext.equals("md") || ext.equals("json") ||
                ext.equals("csv") || ext.equals("xml") || ext.equals("html") ||
                ext.equals("htm") || ext.equals("log") || ext.equals("properties") ||
                ext.equals("yaml") || ext.equals("yml");
    }

    /**
     * Simple language detection based on content.
     */
    private String detectLanguageFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return "unknown";
        }

        // Simple heuristic: check for common Spanish vs English words
        String lowerContent = content.toLowerCase();
        long spanishWords = countOccurrences(lowerContent,
                new String[] { "el", "la", "de", "que", "y", "a", "en", "un", "ser", "se" });
        long englishWords = countOccurrences(lowerContent,
                new String[] { "the", "of", "and", "to", "a", "in", "is", "it", "you", "that" });

        if (spanishWords > englishWords) {
            return "es";
        } else if (englishWords > spanishWords) {
            return "en";
        } else {
            return "unknown";
        }
    }

    private long countOccurrences(String text, String[] words) {
        long count = 0;
        for (String word : words) {
            count += text.split("\\b" + word + "\\b").length - 1;
        }
        return count;
    }

    /**
     * Check if a file type is supported based on extension.
     */
    public boolean isSupported(String extension) {
        if (extension == null)
            return false;
        for (String ext : supportedExtensions) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }
}
