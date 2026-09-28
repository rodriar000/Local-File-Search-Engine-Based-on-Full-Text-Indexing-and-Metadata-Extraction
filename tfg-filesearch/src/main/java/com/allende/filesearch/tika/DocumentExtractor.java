package com.allende.filesearch.tika;

import com.allende.filesearch.model.Config;
import com.allende.filesearch.model.Document;
import com.allende.filesearch.utils.FileUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts text and metadata from a file with Apache Tika.
 *
 * <p>Documents inside other documents (email attachments, files in a ZIP) are
 * read too and their text is added to the container's. Scanned pages are
 * recognised with Tesseract when it is installed (see {@link OcrSupport}).
 */
public class DocumentExtractor {
    private static final Logger logger = LoggerFactory.getLogger(DocumentExtractor.class);

    /**
     * Bump when extraction changes in a way that should re-read files already
     * indexed (new formats, better text). Stored with every document.
     */
    public static final int VERSION = 2;

    public static final List<String> DEFAULT_EXTENSIONS = List.of(
            "txt", "pdf", "docx", "doc", "rtf", "odt", "html", "htm", "xml", "md", "json", "csv",
            "pptx", "ppt", "xlsx", "xls", "msg", "eml", "zip", "tif", "tiff", "jpg", "jpeg", "png");

    /** Text kept per document; the rest of a huge file is dropped. */
    static final int MAX_CHARS = 20_000_000;
    /** Attachments and archive entries read per file (protects against archive bombs). */
    static final int MAX_EMBEDDED = 1_000;
    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;
    private static final int MAX_TITLE_CHARS = 500;
    /** Containers whose images are attachments worth reading (e.g. a scanned JPG sent by email). */
    private static final Set<String> ATTACHMENT_CONTAINERS = Set.of("eml", "msg", "zip");
    private static final Set<String> PLAIN_TEXT = Set.of("txt", "md", "json", "csv", "xml", "html", "htm");

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[ \\t\\u00A0\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n(\\s*\\n)+");

    private final Parser parser;
    private final List<String> supportedExtensions;
    private final Config.OcrConfig ocrConfig;
    private final OcrSupport.Status ocrStatus;

    public DocumentExtractor(List<String> supportedExtensions, Config.OcrConfig ocrConfig) {
        this.supportedExtensions = List.copyOf(supportedExtensions);
        this.ocrConfig = ocrConfig != null ? ocrConfig : disabledOcr();
        this.ocrStatus = OcrSupport.check(this.ocrConfig);
        this.parser = createParser(this.ocrConfig, ocrStatus.available());
        logger.info("Text extraction ready; OCR {}", ocrStatus.available() ? "enabled (" + this.ocrConfig.getLanguage() + ")"
                : "not available: " + ocrStatus.reason());
    }

    /** Without OCR, e.g. for tests and tools that must not depend on Tesseract. */
    public DocumentExtractor(List<String> supportedExtensions) {
        this(supportedExtensions, disabledOcr());
    }

    public DocumentExtractor() {
        this(DEFAULT_EXTENSIONS);
    }

    private static Config.OcrConfig disabledOcr() {
        Config.OcrConfig config = new Config.OcrConfig();
        config.setEnabled(false);
        return config;
    }

    private static Parser createParser(Config.OcrConfig ocr, boolean ocrAvailable) {
        String folder = ocr.getTesseractPath();
        if (!ocrAvailable || folder == null || folder.isBlank()) {
            return new AutoDetectParser();
        }
        // Tesseract's location is a parser setting, not a per-parse one.
        String dir = Path.of(folder).toAbsolutePath() + java.io.File.separator;
        String xml = "<properties><parsers>"
                + "<parser class=\"org.apache.tika.parser.DefaultParser\">"
                + "<parser-exclude class=\"org.apache.tika.parser.ocr.TesseractOCRParser\"/></parser>"
                + "<parser class=\"org.apache.tika.parser.ocr.TesseractOCRParser\"><params>"
                + "<param name=\"tesseractPath\" type=\"string\">" + escapeXml(dir) + "</param>"
                + "</params></parser></parsers></properties>";
        try {
            return new AutoDetectParser(new TikaConfig(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        } catch (TikaException | IOException | SAXException e) {
            logger.warn("Could not configure the OCR location, using the default: {}", e.getMessage());
            return new AutoDetectParser();
        }
    }

    public boolean isOcrAvailable() {
        return ocrStatus.available();
    }

    public OcrSupport.Status ocrStatus() {
        return ocrStatus;
    }

    /** Media types the parser can read, for the doctor command. */
    public Set<MediaType> supportedTypes() {
        return parser.getSupportedTypes(new ParseContext());
    }

    /**
     * Reads a file. Never fails because of the content: a file that cannot be
     * read (damaged, password protected) is returned without text, with the
     * reason in {@link Document#getExtractionError()}, so it can still be found
     * by name.
     *
     * @throws IOException if the file itself cannot be accessed
     */
    public Document extractDocument(Path filePath) throws IOException {
        long start = System.currentTimeMillis();

        Document doc = new Document();
        doc.setPath(filePath.toString());
        doc.setFilename(filePath.getFileName().toString());
        doc.setExtension(FileUtils.getExtension(doc.getFilename()));
        doc.setExtractorVersion(VERSION);
        doc.setOcrAvailable(ocrStatus.available());

        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        doc.setSize(attrs.size());
        doc.setCreatedAt(attrs.creationTime().toInstant());
        doc.setModifiedAt(attrs.lastModifiedTime().toInstant());
        doc.setLastIndexedAt(Instant.now());
        doc.setContent("");

        if (attrs.size() == 0 || attrs.size() > MAX_FILE_SIZE_BYTES) {
            return doc;
        }

        try {
            doc.setChecksumSha256(FileUtils.calculateSHA256(filePath));
        } catch (IOException e) {
            logger.debug("Could not calculate checksum for {}: {}", filePath, e.getMessage());
        }

        Metadata metadata = new Metadata();
        String text;
        try {
            text = parse(filePath, doc.getExtension(), metadata);
        } catch (EncryptedDocumentException e) {
            doc.setExtractionError("Password protected");
            return doc;
        } catch (TikaException | SAXException | RuntimeException e) {
            text = PLAIN_TEXT.contains(lower(doc.getExtension())) ? Files.readString(filePath, StandardCharsets.UTF_8) : null;
            if (text == null) {
                doc.setExtractionError("Could not read the content (damaged or unsupported file)");
                logger.debug("Could not parse {}: {}", filePath, e.toString());
                return doc;
            }
        }

        String header = emailHeader(metadata);
        doc.setContent(normalize(header.isEmpty() ? text : header + "\n\n" + text));
        doc.setTitle(clean(metadata.get(TikaCoreProperties.TITLE)));
        doc.setAuthor(clean(firstNonBlank(metadata.get(Message.MESSAGE_FROM), metadata.get(TikaCoreProperties.CREATOR))));
        doc.setLanguage(detectLanguage(doc.getContent()));

        logger.debug("Extracted {} chars from {} in {} ms", doc.getContent().length(), filePath,
                System.currentTimeMillis() - start);
        return doc;
    }

    private String parse(Path file, String extension, Metadata metadata)
            throws IOException, TikaException, SAXException {
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        context.set(EmbeddedDocumentExtractor.class,
                new LimitedEmbeddedExtractor(context, ATTACHMENT_CONTAINERS.contains(lower(extension))));

        PDFParserConfig pdf = new PDFParserConfig();
        pdf.setSortByPosition(true);
        pdf.setExtractInlineImages(false);
        pdf.setOcrStrategy(ocrStatus.available() ? PDFParserConfig.OCR_STRATEGY.AUTO : PDFParserConfig.OCR_STRATEGY.NO_OCR);
        context.set(PDFParserConfig.class, pdf);

        TesseractOCRConfig tesseract = new TesseractOCRConfig();
        tesseract.setSkipOcr(!ocrStatus.available());
        if (ocrStatus.available()) {
            tesseract.setLanguage(ocrConfig.getLanguage());
            tesseract.setTimeoutSeconds(ocrConfig.getTimeoutSeconds());
        }
        context.set(TesseractOCRConfig.class, tesseract);

        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getFileName().toString());
        BodyContentHandler handler = new BodyContentHandler(MAX_CHARS);
        try (InputStream in = TikaInputStream.get(file)) {
            parser.parse(in, handler, metadata, context);
        } catch (SAXException e) {
            if (!WriteLimitReachedException.isWriteLimitReached(e)) {
                throw e;
            }
            // Keep the first MAX_CHARS characters of a huge document.
        }
        return handler.toString();
    }

    /** Sender, recipients, subject and date of an email, so they can be searched and previewed. */
    private static String emailHeader(Metadata metadata) {
        String from = metadata.get(Message.MESSAGE_FROM);
        if (from == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        addLine(lines, "Asunto", metadata.get(TikaCoreProperties.TITLE));
        addLine(lines, "De", from);
        addLine(lines, "Para", String.join(", ", metadata.getValues(Message.MESSAGE_TO)));
        addLine(lines, "CC", String.join(", ", metadata.getValues(Message.MESSAGE_CC)));
        addLine(lines, "Fecha", metadata.get(TikaCoreProperties.CREATED));
        return String.join("\n", lines);
    }

    private static void addLine(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(label + ": " + value.trim());
        }
    }

    /** Removes control characters and runs of spaces but keeps paragraphs, for the preview. */
    static String normalize(String content) {
        if (content == null) {
            return "";
        }
        String text = CONTROL_CHARS.matcher(content).replaceAll("");
        text = HORIZONTAL_SPACE.matcher(text).replaceAll(" ");
        text = text.replace(" \n", "\n").replace("\n ", "\n");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.trim();
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = CONTROL_CHARS.matcher(value).replaceAll("").trim();
        return trimmed.length() > MAX_TITLE_CHARS ? trimmed.substring(0, MAX_TITLE_CHARS) : trimmed;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Rough Spanish/English guess from common words. */
    private static String detectLanguage(String content) {
        if (content == null || content.isEmpty()) {
            return "unknown";
        }
        String sample = " " + content.substring(0, Math.min(content.length(), 20_000)).toLowerCase()
                .replaceAll("[^\\p{L}]+", " ") + " ";
        int spanish = count(sample, " de ", " la ", " que ", " el ", " en ", " los ", " del ", " se ");
        int english = count(sample, " the ", " of ", " and ", " to ", " in ", " is ", " that ", " for ");
        return spanish > english ? "es" : english > spanish ? "en" : "unknown";
    }

    private static int count(String text, String... words) {
        int total = 0;
        for (String word : words) {
            for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + 1)) {
                total++;
            }
        }
        return total;
    }

    public boolean isSupported(String extension) {
        if (extension == null) {
            return false;
        }
        for (String ext : supportedExtensions) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads embedded documents up to {@link #MAX_EMBEDDED} per file. Images
     * inside ordinary documents (logos, signatures) are skipped; images that
     * are attachments or archive entries are read, with OCR if available.
     */
    private static final class LimitedEmbeddedExtractor extends ParsingEmbeddedDocumentExtractor {
        private final boolean readImages;
        private int count;

        LimitedEmbeddedExtractor(ParseContext context, boolean readImages) {
            super(context);
            this.readImages = readImages;
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            if (++count > MAX_EMBEDDED) {
                return false;
            }
            String type = metadata.get(Metadata.CONTENT_TYPE);
            if (!readImages && type != null && type.startsWith("image/")) {
                return false;
            }
            return super.shouldParseEmbedded(metadata);
        }
    }
}
