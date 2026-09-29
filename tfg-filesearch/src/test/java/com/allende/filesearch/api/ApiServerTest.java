package com.allende.filesearch.api;

import com.allende.filesearch.analytics.AnalyticsManager;
import com.allende.filesearch.index.AppPaths;
import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.IndexSynchronizer;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.tika.DocumentExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiServerTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path tempDir;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private DocumentIndex index;
    private ApiServer server;
    private int port;
    private Path docs;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty(AppPaths.HOME_PROPERTY, tempDir.resolve("home").toString());
        docs = Files.createDirectories(tempDir.resolve("expedientes"));
        index = DocumentIndex.openForWriting(tempDir.resolve("index"));
        IndexSynchronizer synchronizer = new IndexSynchronizer(index, new DocumentExtractor(), new Config());
        server = new ApiServer(index, synchronizer, new AnalyticsManager(), TOKEN);
        port = server.start(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close();
        index.close();
        System.clearProperty(AppPaths.HOME_PROPERTY);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return send(request(path).header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(request(path).header("Authorization", "Bearer " + TOKEN).GET().build());
    }

    @Test
    void refusesShortTokens() {
        assertThatThrownBy(() -> new ApiServer(index, null, null, "short"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresTheSessionToken() throws Exception {
        assertThat(send(request("/api/health").GET().build()).statusCode()).isEqualTo(401);
        assertThat(send(request("/api/health").header("Authorization", "Bearer wrong").GET().build()).statusCode())
                .isEqualTo(401);
        assertThat(get("/api/health").statusCode()).isEqualTo(200);
    }

    @Test
    void healthSaysWhetherScannedDocumentsCanBeRead() throws Exception {
        JsonNode health = json.readTree(get("/api/health").body());
        assertThat(health.path("status").asText()).isEqualTo("ok");
        assertThat(health.path("java").asText()).isNotBlank();
        // The test extractor has OCR switched off.
        assertThat(health.path("ocrAvailable").asBoolean()).isFalse();
        assertThat(health.path("ocrProblem").asText()).isNotBlank();
    }

    @Test
    void refusesBrowserRequestsAndForeignHosts() throws Exception {
        HttpResponse<String> fromPage = send(request("/api/health")
                .header("Authorization", "Bearer " + TOKEN).header("Origin", "https://evil.example").GET().build());
        assertThat(fromPage.statusCode()).isEqualTo(403);

        // DNS rebinding: a page on evil.example resolving to 127.0.0.1 sends its own Host header.
        assertThat(rawStatus("GET /api/health HTTP/1.1\r\nHost: evil.example:" + port
                + "\r\nAuthorization: Bearer " + TOKEN + "\r\nConnection: close\r\n\r\n")).isEqualTo(403);
    }

    @Test
    void validatesRequests() throws Exception {
        assertThat(post("/api/search", "{not json").statusCode()).isEqualTo(400);
        assertThat(send(request("/api/search").header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build()).statusCode()).isEqualTo(400);
        assertThat(post("/api/search", "{\"query\":\"" + "a".repeat(ApiServer.MAX_BODY_BYTES) + "\"}").statusCode())
                .isEqualTo(400);
        assertThat(post("/api/index", "{\"folder\":\"relative/path\"}").statusCode()).isEqualTo(400);
        assertThat(post("/api/index", "{\"folder\":\"" + json(tempDir.resolve("missing")) + "\"}").statusCode())
                .isEqualTo(400);
        assertThat(get("/api/unknown").statusCode()).isEqualTo(404);
    }

    @Test
    void indexesAFolderAndSearchesIt() throws Exception {
        Files.writeString(docs.resolve("demanda.txt"), "Demanda de desahucio por impago de la renta");
        Files.writeString(docs.resolve("notas.txt"), "Notas <img src=x onerror=alert(1)> sobre la fianza");

        HttpResponse<String> started = post("/api/index", "{\"folder\":\"" + json(docs) + "\"}");
        assertThat(started.statusCode()).isEqualTo(202);

        JsonNode status = awaitIndexing();
        assertThat(status.path("lastReport").path("added").asInt()).isEqualTo(2);
        assertThat(status.path("error").isMissingNode()).isTrue();

        JsonNode result = json.readTree(post("/api/search", "{\"query\":\"desahucios\"}").body());
        assertThat(result.path("totalHits").asLong()).isEqualTo(1);
        JsonNode hit = result.path("hits").get(0);
        assertThat(hit.path("filename").asText()).isEqualTo("demanda.txt");
        assertThat(hit.path("snippet").asText()).contains(DocumentIndex.HIGHLIGHT_PRE + "desahucio");
        assertThat(hit.has("content")).isFalse();

        JsonNode stats = json.readTree(get("/api/stats").body());
        assertThat(stats.path("documentCount").asLong()).isEqualTo(2);
        assertThat(stats.path("fileTypes").path("txt").asLong()).isEqualTo(2);
    }

    @Test
    void previewsIndexedDocumentsOnly() throws Exception {
        Path demanda = docs.resolve("demanda.txt");
        Files.writeString(demanda, "Demanda de desahucio.\n\nSe reclama la fianza y las rentas.");
        Path outside = Files.writeString(tempDir.resolve("secreto.txt"), "no indexado");
        post("/api/index", "{\"folder\":\"" + json(docs) + "\"}");
        awaitIndexing();

        JsonNode preview = json.readTree(post("/api/preview",
                "{\"path\":\"" + json(demanda) + "\",\"query\":\"fianza\"}").body());
        assertThat(preview.path("filename").asText()).isEqualTo("demanda.txt");
        assertThat(preview.path("text").asText())
                .contains("Demanda de desahucio.\n\nSe reclama la " + DocumentIndex.HIGHLIGHT_PRE + "fianza");
        assertThat(preview.path("truncated").asBoolean()).isFalse();

        assertThat(post("/api/preview", "{\"path\":\"" + json(outside) + "\"}").statusCode()).isEqualTo(404);
        assertThat(post("/api/preview", "{}").statusCode()).isEqualTo(400);
    }

    @Test
    void findsDocumentsByIdentifierAndListsThemForDataProtectionRequests() throws Exception {
        Path demanda = docs.resolve("demanda.txt");
        Files.writeString(demanda, "Juan Perez Garcia, DNI 12.345.678-Z, procedimiento ordinario 456/2024. Tel. 612 345 678");
        Files.writeString(docs.resolve("nota.txt"), "Llamar a Juan Perez Garcia por el recurso.");
        Files.writeString(docs.resolve("otro.txt"), "Cliente con NIE X1234567L");
        post("/api/index", "{\"folder\":\"" + json(docs) + "\"}");
        awaitIndexing();

        JsonNode byDni = json.readTree(post("/api/search", "{\"query\":\"\",\"identifier\":\"12345678z\"}").body());
        assertThat(byDni.path("totalHits").asLong()).isEqualTo(1);
        assertThat(byDni.path("hits").get(0).path("dataTypes").toString()).isEqualTo("[\"dni\",\"telefono\",\"procedimiento\"]");
        assertThat(json.readTree(post("/api/search", "{\"identifier\":\"456/2024\"}").body()).path("totalHits").asLong())
                .isEqualTo(1);
        assertThat(post("/api/search", "{\"identifier\":\"12345678A\"}").statusCode()).isEqualTo(400);
        assertThat(post("/api/search", "{\"dataTypes\":[\"secreto\"]}").statusCode()).isEqualTo(400);

        HttpResponse<String> response = post("/api/report", "{\"name\":\"  juan   perez garcia \",\"identifier\":\"12.345.678-Z\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode report = json.readTree(response.body());
        assertThat(report.path("name").asText()).isEqualTo("juan perez garcia");
        assertThat(report.path("identifier").asText()).isEqualTo("12345678Z");
        assertThat(report.path("identifierType").asText()).isEqualTo("dni");
        assertThat(report.path("total").asLong()).isEqualTo(2);
        assertThat(report.path("documents").get(0).path("filename").asText()).isEqualTo("demanda.txt");
        assertThat(report.path("documents").get(0).path("byIdentifier").asBoolean()).isTrue();
        assertThat(report.path("documents").get(1).path("byIdentifier").asBoolean()).isFalse();

        assertThat(post("/api/report", "{}").statusCode()).isEqualTo(400);
        assertThat(post("/api/report", "{\"name\":\"ab\"}").statusCode()).isEqualTo(400);
        assertThat(post("/api/report", "{\"identifier\":\"hola\"}").statusCode()).isEqualTo(400);

        JsonNode preview = json.readTree(post("/api/preview", "{\"path\":\"" + json(demanda) + "\"}").body());
        assertThat(preview.path("personalData").findValuesAsText("type")).containsExactly("dni", "telefono");
    }

    @Test
    void switchingFoldersRemovesTheOldOne() throws Exception {
        Files.writeString(docs.resolve("demanda.txt"), "Demanda de desahucio");
        Path other = Files.createDirectories(tempDir.resolve("otro"));
        Files.writeString(other.resolve("poder.txt"), "Poder notarial");

        post("/api/index", "{\"folder\":\"" + json(docs) + "\"}");
        awaitIndexing();
        post("/api/index", "{\"folder\":\"" + json(other) + "\"}");
        JsonNode status = awaitIndexing();

        assertThat(status.path("lastReport").path("deleted").asInt()).isEqualTo(1);
        assertThat(json.readTree(post("/api/search", "{\"query\":\"desahucio\"}").body()).path("totalHits").asLong())
                .isZero();
    }

    private JsonNode awaitIndexing() throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode status = json.readTree(get("/api/index/status").body());
            if (!status.path("running").asBoolean()) {
                return status;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("indexing did not finish");
    }

    private static String json(Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    private int rawStatus(String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            String statusLine = new String(in.readNBytes(12), StandardCharsets.US_ASCII);
            return Integer.parseInt(statusLine.substring(9, 12));
        }
    }
}
