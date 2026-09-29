package com.allende.filesearch.api;

import com.allende.filesearch.analytics.AnalyticsManager;
import com.allende.filesearch.entities.EntityExtractor;
import com.allende.filesearch.entities.EntityType;
import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.DocumentPreview;
import com.allende.filesearch.index.IndexSynchronizer;
import com.allende.filesearch.index.SearchRequest;
import com.allende.filesearch.model.Document;
import com.allende.filesearch.model.SearchResult;
import com.allende.filesearch.tika.OcrSupport;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local HTTP API used by the desktop app.
 *
 * <p>It only listens on the loopback interface and every request must carry
 * the session token the app generated when it started this process. Requests
 * with an Origin header (i.e. from a web page) or an unexpected Host header
 * (DNS rebinding) are refused before the token is even checked.
 */
public final class ApiServer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);

    /** Tokens shorter than this are refused: they could be guessed. */
    public static final int MIN_TOKEN_LENGTH = 32;
    static final int MAX_BODY_BYTES = 64 * 1024;

    private final DocumentIndex index;
    private final AnalyticsManager analytics;
    private final IndexingJob indexing;
    private final IndexSynchronizer synchronizer;
    private final byte[] expectedAuthorization;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private HttpServer server;
    private ExecutorService executor;
    private int port;

    public ApiServer(DocumentIndex index, IndexSynchronizer synchronizer, AnalyticsManager analytics, String token) {
        if (token == null || token.length() < MIN_TOKEN_LENGTH) {
            throw new IllegalArgumentException("API token must have at least " + MIN_TOKEN_LENGTH + " characters");
        }
        this.index = index;
        this.analytics = analytics;
        this.synchronizer = synchronizer;
        this.indexing = new IndexingJob(synchronizer, analytics);
        this.expectedAuthorization = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
    }

    /** Starts listening on 127.0.0.1; port 0 picks a free port. */
    public int start(int requestedPort) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), requestedPort), 0);
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "api");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        port = server.getAddress().getPort();
        logger.info("Local API listening on 127.0.0.1:{}", port);
        return port;
    }

    public int port() {
        return port;
    }

    // ----------------------------------------------------------------- routing

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isLocalHost(exchange.getRequestHeaders().getFirst("Host"))
                    || exchange.getRequestHeaders().containsKey("Origin")) {
                send(exchange, 403, error("forbidden"));
                return;
            }
            if (!isAuthorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
                send(exchange, 401, error("unauthorized"));
                return;
            }
            route(exchange);
        } catch (BadRequest e) {
            send(exchange, 400, error(e.getMessage()));
        } catch (Exception e) {
            logger.error("API request failed: {}", e.getClass().getSimpleName());
            send(exchange, 500, error("internal error"));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        switch (method + " " + path) {
            case "GET /api/health" -> send(exchange, 200, health());
            case "GET /api/stats" -> send(exchange, 200, index.summary());
            case "POST /api/search" -> send(exchange, 200, search(readJson(exchange, SearchBody.class)));
            case "POST /api/preview" -> preview(exchange, readJson(exchange, PreviewBody.class));
            case "POST /api/report" -> send(exchange, 200, report(readJson(exchange, ReportBody.class)));
            case "POST /api/index" -> startIndexing(exchange, readJson(exchange, IndexBody.class));
            case "GET /api/index/status" -> send(exchange, 200, indexing.status());
            case "POST /api/index/cancel" -> {
                indexing.cancel();
                send(exchange, 202, indexing.status());
            }
            default -> send(exchange, 404, error("not found"));
        }
    }

    // --------------------------------------------------------------- endpoints

    /** No document names or contents: it is copied into support requests. */
    record HealthResponse(String status, String java, String os, boolean ocrAvailable, String ocrProblem) {
    }

    private HealthResponse health() {
        OcrSupport.Status ocr = synchronizer.ocrStatus();
        return new HealthResponse("ok", System.getProperty("java.version"),
                System.getProperty("os.name") + " " + System.getProperty("os.version"), ocr.available(), ocr.reason());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchBody(String query, List<String> extensions, Long sizeMinBytes, Long sizeMaxBytes,
            Instant modifiedFrom, Instant modifiedTo, String identifier, List<String> dataTypes,
            Integer from, Integer size) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReportBody(String name, String identifier) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IndexBody(String folder) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PreviewBody(String path, String query) {
    }

    record PreviewResponse(String path, String filename, String extension, long size, Instant modifiedAt,
            String title, String author, String text, boolean truncated,
            List<DocumentPreview.PersonalData> personalData) {
    }

    /** Text of an indexed document; paths that are not in the index are refused, so no other file can be read. */
    private void preview(HttpExchange exchange, PreviewBody body) throws IOException {
        if (body == null || body.path() == null || body.path().isBlank()) {
            throw new BadRequest("path is required");
        }
        DocumentPreview preview;
        try {
            preview = index.preview(body.path(), body.query());
        } catch (IllegalArgumentException e) {
            throw new BadRequest("invalid search");
        }
        if (preview == null) {
            send(exchange, 404, error("not in the index"));
            return;
        }
        Document doc = preview.document();
        send(exchange, 200, new PreviewResponse(doc.getPath(), doc.getFilename(), doc.getExtension(), doc.getSize(),
                doc.getModifiedAt(), doc.getTitle(), doc.getAuthor(), preview.text(), preview.truncated(),
                preview.personalData()));
    }

    /** One result as the app shows it; never the full document text. */
    record Hit(String path, String filename, String extension, long size, Instant modifiedAt, Instant createdAt,
            String title, String author, double score, String snippet, List<String> dataTypes) {
    }

    record SearchResponse(long totalHits, long tookMs, List<Hit> hits) {
    }

    private SearchResponse search(SearchBody body) throws IOException {
        if (body == null) {
            throw new BadRequest("missing body");
        }
        EntityExtractor.Entity identifier = identifier(body.identifier());
        SearchRequest request = new SearchRequest(body.query(), body.extensions(), body.sizeMinBytes(),
                body.sizeMaxBytes(), body.modifiedFrom(), body.modifiedTo(),
                identifier == null ? null : identifier.term(), dataTypes(body.dataTypes()),
                body.from() == null ? 0 : body.from(), body.size() == null ? 20 : body.size());
        SearchResult result;
        try {
            result = index.search(request);
        } catch (IllegalArgumentException e) {
            throw new BadRequest("invalid search");
        }
        try {
            analytics.recordSearch(request.query(), result.getTotalTimeMs(), result.getTotalHits());
        } catch (RuntimeException e) {
            logger.debug("Could not record search stats: {}", e.getMessage());
        }
        List<Hit> hits = result.getHits().stream().map(ApiServer::toHit).toList();
        return new SearchResponse(result.getTotalHits(), result.getTotalTimeMs(), hits);
    }

    private static Hit toHit(SearchResult.DocumentHit hit) {
        Document doc = hit.getDocument();
        String snippet = hit.getHighlights() != null && !hit.getHighlights().isEmpty()
                ? hit.getHighlights().get(0)
                : null;
        return new Hit(doc.getPath(), doc.getFilename(), doc.getExtension(), doc.getSize(), doc.getModifiedAt(),
                doc.getCreatedAt(), doc.getTitle(), doc.getAuthor(), hit.getScore(), snippet, doc.getDataTypes());
    }

    /** What the user typed as an identifier; refused when it is not one, so a typo does not look like "no documents". */
    private static EntityExtractor.Entity identifier(String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        if (typed.length() > 100) {
            throw new BadRequest("unrecognised identifier");
        }
        EntityExtractor.Entity entity = EntityExtractor.parseIdentifier(typed);
        if (entity == null) {
            throw new BadRequest("unrecognised identifier");
        }
        return entity;
    }

    private static List<String> dataTypes(List<String> keys) {
        if (keys == null) {
            return List.of();
        }
        for (String key : keys) {
            if (EntityType.fromKey(key).isEmpty()) {
                throw new BadRequest("unknown data type");
            }
        }
        return keys;
    }

    record ReportDocument(String path, String filename, String extension, Instant modifiedAt,
            boolean byName, boolean byIdentifier, List<String> dataTypes) {
    }

    /**
     * @param identifier     the identifier as normalised, e.g. "12345678Z"
     * @param identifierType its kind (EntityType key)
     */
    record ReportResponse(String name, String identifier, String identifierType, Instant generatedAt,
            long total, boolean truncated, List<ReportDocument> documents) {
    }

    /** Documents that mention a person, for RGPD requests. Not recorded in the search statistics. */
    private ReportResponse report(ReportBody body) throws IOException {
        if (body == null) {
            throw new BadRequest("missing body");
        }
        String name = body.name() == null ? null : body.name().strip().replaceAll("\\s+", " ");
        if (name != null && name.isEmpty()) {
            name = null;
        }
        if (name != null && (name.length() < 3 || name.length() > 200)) {
            throw new BadRequest("invalid name");
        }
        EntityExtractor.Entity identifier = identifier(body.identifier());
        if (name == null && identifier == null) {
            throw new BadRequest("name or identifier required");
        }
        DocumentIndex.Report report;
        try {
            report = index.report(name, identifier == null ? null : identifier.term());
        } catch (IllegalArgumentException e) {
            throw new BadRequest("invalid search");
        }
        List<ReportDocument> documents = report.entries().stream().map(entry -> {
            Document doc = entry.document();
            return new ReportDocument(doc.getPath(), doc.getFilename(), doc.getExtension(), doc.getModifiedAt(),
                    entry.byName(), entry.byIdentifier(), doc.getDataTypes());
        }).toList();
        return new ReportResponse(name, identifier == null ? null : identifier.value(),
                identifier == null ? null : identifier.type().key(), Instant.now(),
                report.total(), report.truncated(), documents);
    }

    private void startIndexing(HttpExchange exchange, IndexBody body) throws IOException {
        if (body == null || body.folder() == null || body.folder().isBlank()) {
            throw new BadRequest("folder is required");
        }
        Path folder;
        try {
            folder = Path.of(body.folder());
        } catch (RuntimeException e) {
            throw new BadRequest("invalid folder");
        }
        if (!folder.isAbsolute()) {
            throw new BadRequest("folder must be an absolute path");
        }
        folder = folder.normalize();
        if (!Files.isDirectory(folder)) {
            throw new BadRequest("folder does not exist");
        }
        if (!indexing.start(folder)) {
            send(exchange, 409, error("indexing already running"));
            return;
        }
        send(exchange, 202, indexing.status());
    }

    // ----------------------------------------------------------------- helpers

    private boolean isLocalHost(String host) {
        return ("127.0.0.1:" + port).equals(host) || ("localhost:" + port).equals(host);
    }

    private boolean isAuthorized(String header) {
        byte[] given = header == null ? new byte[0] : header.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedAuthorization, given);
    }

    private <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new BadRequest("expected application/json");
        }
        byte[] body;
        try (InputStream in = exchange.getRequestBody()) {
            body = in.readNBytes(MAX_BODY_BYTES + 1);
        }
        if (body.length > MAX_BODY_BYTES) {
            throw new BadRequest("body too large");
        }
        try {
            return json.readValue(body, type);
        } catch (IOException e) {
            throw new BadRequest("invalid JSON");
        }
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static Map<String, String> error(String message) {
        return Map.of("error", message);
    }

    private static final class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }

    @Override
    public void close() {
        indexing.close();
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
