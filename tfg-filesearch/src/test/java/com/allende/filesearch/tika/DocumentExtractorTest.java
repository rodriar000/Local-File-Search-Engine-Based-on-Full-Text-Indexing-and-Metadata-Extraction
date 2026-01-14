package com.allende.filesearch.tika;

import com.allende.filesearch.model.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test for DocumentExtractor.
 * Tests real content extraction from various file formats.
 */
class DocumentExtractorTest {

    private DocumentExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new DocumentExtractor();
    }

    @Test
    void testExtractTxtFile() throws IOException {
        // Create a test TXT file
        String testContent = "This is a test document.\nElasticsearch is awesome!\nFull-text search works.";
        Path txtFile = tempDir.resolve("test.txt");
        Files.writeString(txtFile, testContent);

        // Extract
        Document doc = extractor.extractDocument(txtFile);

        // Verify
        assertNotNull(doc);
        assertNotNull(doc.getContent(), "Content should not be null");
        assertFalse(doc.getContent().isEmpty(), "Content should not be empty");
        assertTrue(doc.getContent().contains("Elasticsearch"), "Content should contain 'Elasticsearch'");
        assertTrue(doc.getContent().contains("search"), "Content should contain 'search'");

        System.out.println("TXT extraction verified: " + doc.getContent().length() + " chars");
    }

    @Test
    void testExtractMarkdownFile() throws IOException {
        // Create a test Markdown file
        String testContent = "# Heading\n\nThis is **bold** text.\n\nElasticsearch integration test.";
        Path mdFile = tempDir.resolve("test.md");
        Files.writeString(mdFile, testContent);

        // Extract
        Document doc = extractor.extractDocument(mdFile);

        // Verify
        assertNotNull(doc);
        assertNotNull(doc.getContent());
        assertFalse(doc.getContent().isEmpty());
        assertTrue(doc.getContent().contains("Elasticsearch"));

        System.out.println("Markdown extraction verified: " + doc.getContent().length() + " chars");
    }

    @Test
    void testExtractJsonFile() throws IOException {
        // Create a test JSON file
        String testContent = "{\"name\":\"test\",\"description\":\"Elasticsearch document\",\"active\":true}";
        Path jsonFile = tempDir.resolve("test.json");
        Files.writeString(jsonFile, testContent);

        // Extract
        Document doc = extractor.extractDocument(jsonFile);

        // Verify
        assertNotNull(doc);
        assertNotNull(doc.getContent());
        assertFalse(doc.getContent().isEmpty());
        assertTrue(doc.getContent().contains("Elasticsearch") || doc.getContent().contains("document"));

        System.out.println("JSON extraction verified: " + doc.getContent().length() + " chars");
    }

    @Test
    void testExtractHtmlFile() throws IOException {
        // Create a test HTML file
        String testContent = "<html><head><title>Test</title></head><body><h1>Elasticsearch</h1><p>Content extraction test</p></body></html>";
        Path htmlFile = tempDir.resolve("test.html");
        Files.writeString(htmlFile, testContent);

        // Extract
        Document doc = extractor.extractDocument(htmlFile);

        // Verify
        assertNotNull(doc);
        assertNotNull(doc.getContent());
        assertFalse(doc.getContent().isEmpty());
        // Tika extracts text content, removing HTML tags
        assertTrue(doc.getContent().contains("Elasticsearch") || doc.getContent().contains("Content"));

        System.out.println("HTML extraction verified: " + doc.getContent().length() + " chars");
    }

    @Test
    void testEmptyFile() throws IOException {
        // Create an empty file
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "");

        // Extract
        Document doc = extractor.extractDocument(emptyFile);

        // Verify - should handle empty files gracefully
        assertNotNull(doc);
        assertNotNull(doc.getContent());
        assertTrue(doc.getContent().isEmpty(), "Empty file should have empty content");

        System.out.println("Empty file handled correctly");
    }

    @Test
    void testFileMetadata() throws IOException {
        // Create a test file
        String testContent = "Test content for metadata extraction";
        Path testFile = tempDir.resolve("metadata_test.txt");
        Files.writeString(testFile, testContent);

        // Extract
        Document doc = extractor.extractDocument(testFile);

        // Verify metadata
        assertNotNull(doc.getPath());
        assertEquals("metadata_test.txt", doc.getFilename());
        assertEquals("txt", doc.getExtension());
        assertTrue(doc.getSize() > 0);
        assertNotNull(doc.getCreatedAt());
        assertNotNull(doc.getModifiedAt());
        assertNotNull(doc.getLastIndexedAt());

        System.out.println("Metadata extraction verified");
    }

    @Test
    void testSupportedExtensions() {
        assertTrue(extractor.isSupported("txt"));
        assertTrue(extractor.isSupported("pdf"));
        assertTrue(extractor.isSupported("docx"));
        assertTrue(extractor.isSupported("md"));
        assertTrue(extractor.isSupported("json"));
        assertFalse(extractor.isSupported("exe"));
        assertFalse(extractor.isSupported("bin"));

        System.out.println("Extension support check passed");
    }

    @Test
    void testContentNotNull() throws IOException {
        // Create files with various content
        String[] contents = {
                "Simple text content",
                "Multi\nline\ncontent\nwith\nbreaks",
                "Content with special characters: @#$%^&*()",
                "UTF-8 content: áéíóú ñ ¿¡"
        };

        for (int i = 0; i < contents.length; i++) {
            Path file = tempDir.resolve("test_" + i + ".txt");
            Files.writeString(file, contents[i]);

            Document doc = extractor.extractDocument(file);

            assertNotNull(doc.getContent(), "Content should never be null for file " + i);
            assertFalse(doc.getContent().isEmpty(), "Content should not be empty for file " + i);
        }

        System.out.println("All content extractions returned non-null, non-empty content");
    }
}
