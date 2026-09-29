package com.allende.filesearch.index;

import com.allende.filesearch.model.Document;
import com.allende.filesearch.model.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentIndexTest {

    @TempDir
    Path tempDir;

    private static Document doc(String path, String content, String ext, long size, Instant modified) {
        Document d = new Document();
        d.setPath(path);
        d.setFilename(Path.of(path).getFileName().toString());
        d.setExtension(ext);
        d.setSize(size);
        d.setModifiedAt(modified);
        d.setContent(content);
        return d;
    }

    private static List<String> paths(SearchResult result) {
        return result.getHits().stream().map(h -> h.getDocument().getPath()).toList();
    }

    private DocumentIndex indexWithSamples() throws Exception {
        DocumentIndex index = DocumentIndex.openForWriting(tempDir.resolve("index"));
        index.upsert(doc("/exp/garcia/demanda.pdf",
                "Demanda de desahucio por falta de pago. Contrato de arrendamiento con fianza de dos mensualidades.",
                "pdf", 1000, Instant.parse("2025-03-01T10:00:00Z")));
        index.upsert(doc("/exp/lopez/escrito.docx",
                "Escrito sobre la cláusula suelo del préstamo hipotecario.",
                "docx", 5000, Instant.parse("2026-01-15T10:00:00Z")));
        index.upsert(doc("/exp/lopez/notas.txt",
                "Notas: revisar desahucios pendientes y la fianza.",
                "txt", 200, Instant.parse("2026-02-01T10:00:00Z")));
        index.commit();
        return index;
    }

    @Test
    void matchesSpanishInflectionsAndIgnoresAccents() throws Exception {
        try (DocumentIndex index = indexWithSamples()) {
            assertThat(paths(index.search(SearchRequest.of("desahucios", 10))))
                    .containsExactlyInAnyOrder("/exp/garcia/demanda.pdf", "/exp/lopez/notas.txt");
            assertThat(paths(index.search(SearchRequest.of("clausula", 10))))
                    .containsExactly("/exp/lopez/escrito.docx");
            assertThat(paths(index.search(SearchRequest.of("HIPOTECARIOS", 10))))
                    .containsExactly("/exp/lopez/escrito.docx");
        }
    }

    @Test
    void requiresAllWordsAndSupportsPhrasesExclusionAndProximity() throws Exception {
        try (DocumentIndex index = indexWithSamples()) {
            assertThat(paths(index.search(SearchRequest.of("fianza desahucio", 10))))
                    .containsExactlyInAnyOrder("/exp/garcia/demanda.pdf", "/exp/lopez/notas.txt");
            assertThat(paths(index.search(SearchRequest.of("\"falta de pago\"", 10))))
                    .containsExactly("/exp/garcia/demanda.pdf");
            assertThat(paths(index.search(SearchRequest.of("fianza -arrendamiento", 10))))
                    .containsExactly("/exp/lopez/notas.txt");
            assertThat(paths(index.search(SearchRequest.of("\"arrendamiento mensualidades\"~5", 10))))
                    .containsExactly("/exp/garcia/demanda.pdf");
            assertThat(paths(index.search(SearchRequest.of("\"arrendamiento mensualidades\"", 10)))).isEmpty();
            assertThat(paths(index.search(SearchRequest.of("hipote*", 10))))
                    .containsExactly("/exp/lopez/escrito.docx");
        }
    }

    @Test
    void searchesFileNames() throws Exception {
        try (DocumentIndex index = indexWithSamples()) {
            assertThat(paths(index.search(SearchRequest.of("notas", 10)))).containsExactly("/exp/lopez/notas.txt");

            index.upsert(doc("/exp/ruiz/Poder_notarial-v2.pdf", "", "pdf", 10, Instant.now()));
            index.commit();
            assertThat(paths(index.search(SearchRequest.of("notarial", 10))))
                    .containsExactly("/exp/ruiz/Poder_notarial-v2.pdf");
            assertThat(index.search(SearchRequest.of("notarial", 10)).getHits().get(0).getDocument().getFilename())
                    .isEqualTo("Poder_notarial-v2.pdf");
        }
    }

    @Test
    void previewsTheWholeDocumentWithEveryMatchMarked() throws Exception {
        try (DocumentIndex index = indexWithSamples()) {
            DocumentPreview preview = index.preview("/exp/garcia/demanda.pdf", "fianza desahucios");
            assertThat(preview.document().getFilename()).isEqualTo("demanda.pdf");
            assertThat(preview.text())
                    .startsWith("Demanda de " + DocumentIndex.HIGHLIGHT_PRE + "desahucio" + DocumentIndex.HIGHLIGHT_POST)
                    .contains(DocumentIndex.HIGHLIGHT_PRE + "fianza" + DocumentIndex.HIGHLIGHT_POST)
                    .endsWith("mensualidades.");
            assertThat(preview.truncated()).isFalse();

            assertThat(index.preview("/exp/garcia/demanda.pdf", "").text()).doesNotContain(DocumentIndex.HIGHLIGHT_PRE);
            assertThat(index.preview("/exp/garcia/demanda.pdf", "notas").text()).startsWith("Demanda de desahucio");
            assertThat(index.preview("/etc/passwd", "root")).isNull();
        }
    }

    @Test
    void appliesFilters() throws Exception {
        try (DocumentIndex index = indexWithSamples()) {
            SearchRequest byType = new SearchRequest("fianza", List.of("PDF"), null, null, null, null, 0, 10);
            assertThat(paths(index.search(byType))).containsExactly("/exp/garcia/demanda.pdf");

            SearchRequest bySize = new SearchRequest("", List.of(), 1000L, 5000L, null, null, 0, 10);
            assertThat(paths(index.search(bySize)))
                    .containsExactlyInAnyOrder("/exp/garcia/demanda.pdf", "/exp/lopez/escrito.docx");

            SearchRequest byDate = new SearchRequest("", List.of(), null, null,
                    Instant.parse("2026-01-01T00:00:00Z"), null, 0, 10);
            assertThat(paths(index.search(byDate)))
                    .containsExactlyInAnyOrder("/exp/lopez/escrito.docx", "/exp/lopez/notas.txt");
        }
    }

    @Test
    void highlightsWithMarkersAndNeverAsHtml() throws Exception {
        try (DocumentIndex index = DocumentIndex.openForWriting(tempDir.resolve("index"))) {
            index.upsert(doc("/x/a.txt", "texto <img src=x onerror=alert(1)> del contrato firmado", "txt", 10, Instant.now()));
            index.commit();

            String snippet = index.search(SearchRequest.of("contrato", 10)).getHits().get(0).getHighlights().get(0);
            assertThat(snippet).contains(DocumentIndex.HIGHLIGHT_PRE + "contrato" + DocumentIndex.HIGHLIGHT_POST);
            assertThat(snippet).contains("<img src=x onerror=alert(1)>");
            assertThat(snippet).doesNotContain("<b>", "<em>", "&lt;");
        }
    }

    @Test
    void highlightsMatchesBeyondTheFirstTenThousandCharacters() throws Exception {
        try (DocumentIndex index = DocumentIndex.openForWriting(tempDir.resolve("index"))) {
            String longText = "relleno ".repeat(5000) + "usucapión extraordinaria";
            index.upsert(doc("/x/long.txt", longText, "txt", longText.length(), Instant.now()));
            index.commit();

            String snippet = index.search(SearchRequest.of("usucapion", 10)).getHits().get(0).getHighlights().get(0);
            assertThat(snippet).contains(DocumentIndex.HIGHLIGHT_PRE + "usucapión" + DocumentIndex.HIGHLIGHT_POST);
        }
    }

    @Test
    void upsertReplacesAndDeleteRemoves() throws Exception {
        try (DocumentIndex index = indexWithSamples()) {
            index.upsert(doc("/exp/lopez/notas.txt", "Notas nuevas sin coincidencias", "txt", 30, Instant.now()));
            index.delete("/exp/garcia/demanda.pdf");
            index.commit();

            assertThat(index.search(SearchRequest.of("fianza", 10)).getTotalHits()).isZero();
            assertThat(index.summary().documentCount()).isEqualTo(2);
        }
    }

    @Test
    void summarisesDocumentsByType() throws Exception {
        try (DocumentIndex index = indexWithSamples()) {
            IndexSummary summary = index.summary();
            assertThat(summary.exists()).isTrue();
            assertThat(summary.documentCount()).isEqualTo(3);
            assertThat(summary.fileTypes()).containsEntry("pdf", 1L).containsEntry("docx", 1L).containsEntry("txt", 1L);
            assertThat(summary.sizeBytes()).isPositive();
        }
    }

    @Test
    void readOnlyInstanceSeesCommitsFromTheWriter() throws Exception {
        Path location = tempDir.resolve("index");
        try (DocumentIndex reader = DocumentIndex.openReadOnly(location)) {
            assertThat(reader.summary().exists()).isFalse();
            assertThat(reader.search(SearchRequest.of("fianza", 10)).getTotalHits()).isZero();

            try (DocumentIndex writer = DocumentIndex.openForWriting(location)) {
                writer.upsert(doc("/a/b.txt", "fianza", "txt", 5, Instant.now()));
                writer.commit();
                assertThat(reader.search(SearchRequest.of("fianza", 10)).getTotalHits()).isEqualTo(1);
            }
        }
    }

    @Test
    void secondWriterGetsAClearError() throws Exception {
        Path location = tempDir.resolve("index");
        try (DocumentIndex ignored = DocumentIndex.openForWriting(location)) {
            assertThatThrownBy(() -> DocumentIndex.openForWriting(location))
                    .isInstanceOf(IndexLockedException.class)
                    .hasMessageContaining("in use by another process");
        }
    }

    @Test
    void toleratesQueriesWithOnlyStopWordsOrOperators() throws Exception {
        try (DocumentIndex index = indexWithSamples()) {
            assertThat(index.search(SearchRequest.of("de la", 10)).getTotalHits()).isZero();
            assertThat(index.search(SearchRequest.of("\"unclosed ( -", 10)).getTotalHits()).isZero();
        }
    }

    @Test
    void paginatesAndCapsPageSize() throws Exception {
        try (DocumentIndex index = indexWithSamples()) {
            SearchResult page2 = index.search(new SearchRequest("", List.of(), null, null, null, null, 2, 2));
            assertThat(page2.getTotalHits()).isEqualTo(3);
            assertThat(page2.getHits()).hasSize(1);

            SearchResult beyond = index.search(new SearchRequest("", List.of(), null, null, null, null, 50, 10));
            assertThat(beyond.getHits()).isEmpty();
        }
    }

    private DocumentIndex indexWithPersonalData() throws Exception {
        DocumentIndex index = DocumentIndex.openForWriting(tempDir.resolve("index"));
        index.upsert(doc("/exp/perez/demanda.pdf",
                "Don Juan Pérez García, con DNI 12.345.678-Z, en el procedimiento ordinario 456/2024.",
                "pdf", 1000, Instant.parse("2025-03-01T10:00:00Z")));
        index.upsert(doc("/exp/perez/transferencia.pdf",
                "Transferencia a la cuenta ES91 2100 0418 4502 0005 1332 de 12345678Z.",
                "pdf", 1000, Instant.parse("2025-03-02T10:00:00Z")));
        index.upsert(doc("/exp/lopez/informe.docx",
                "Informe médico de Ana López: baja médica por incapacidad temporal. Juan Pérez García declara.",
                "docx", 1000, Instant.parse("2025-03-03T10:00:00Z")));
        index.commit();
        return index;
    }

    private static SearchRequest withData(String entity, List<String> types) {
        return new SearchRequest("", List.of(), null, null, null, null, entity, types, 0, 10);
    }

    @Test
    void filtersByIdentifierHoweverItWasWritten() throws Exception {
        try (DocumentIndex index = indexWithPersonalData()) {
            assertThat(paths(index.search(withData("dni:12345678Z", List.of()))))
                    .containsExactlyInAnyOrder("/exp/perez/demanda.pdf", "/exp/perez/transferencia.pdf");
            assertThat(paths(index.search(withData("procedimiento:456/2024", List.of()))))
                    .containsExactly("/exp/perez/demanda.pdf");
            assertThat(paths(index.search(withData("iban:ES9121000418450200051332", List.of()))))
                    .containsExactly("/exp/perez/transferencia.pdf");
            assertThat(paths(index.search(withData(null, List.of("salud")))))
                    .containsExactly("/exp/lopez/informe.docx");
            assertThat(paths(index.search(withData(null, List.of("salud", "iban")))))
                    .containsExactlyInAnyOrder("/exp/lopez/informe.docx", "/exp/perez/transferencia.pdf");
        }
    }

    @Test
    void resultsSayWhichKindsOfPersonalDataADocumentHolds() throws Exception {
        try (DocumentIndex index = indexWithPersonalData()) {
            SearchResult result = index.search(SearchRequest.of("transferencia", 10));
            assertThat(result.getHits().get(0).getDocument().getDataTypes()).containsExactly("dni", "iban");
        }
    }

    @Test
    void reportListsEveryDocumentThatMentionsThePersonAndWhy() throws Exception {
        try (DocumentIndex index = indexWithPersonalData()) {
            DocumentIndex.Report report = index.report("juan perez garcia", "dni:12345678Z");
            assertThat(report.total()).isEqualTo(3);
            assertThat(report.truncated()).isFalse();
            assertThat(report.entries()).extracting(e -> e.document().getPath())
                    .containsExactly("/exp/lopez/informe.docx", "/exp/perez/demanda.pdf", "/exp/perez/transferencia.pdf");
            assertThat(report.entries()).extracting(DocumentIndex.ReportEntry::byName)
                    .containsExactly(true, true, false);
            assertThat(report.entries()).extracting(DocumentIndex.ReportEntry::byIdentifier)
                    .containsExactly(false, true, true);

            assertThat(index.report("Ana Pérez", null).entries()).isEmpty();
            assertThatThrownBy(() -> index.report(" ", null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void previewMarksPersonalDataEvenInsideSearchMatches() throws Exception {
        try (DocumentIndex index = indexWithPersonalData()) {
            DocumentPreview preview = index.preview("/exp/perez/transferencia.pdf", "12345678Z");
            String text = preview.text();
            assertThat(preview.personalData()).extracting(DocumentPreview.PersonalData::type)
                    .containsExactly("iban", "dni");
            DocumentPreview.PersonalData dni = preview.personalData().get(1);
            assertThat(text.substring(dni.start() - 1, dni.end() + 1))
                    .isEqualTo(DocumentIndex.HIGHLIGHT_PRE + "12345678Z" + DocumentIndex.HIGHLIGHT_POST);
        }
    }

    @Test
    void findsPersonalDataInEntriesIndexedBeforeItWasDetectedWithoutReadingTheFilesAgain() throws Exception {
        Path location = tempDir.resolve("index");
        Document old = doc("/exp/perez/demanda.pdf", "Cliente con DNI 12345678Z.", "pdf", 1000,
                Instant.parse("2025-03-01T10:00:00Z"));
        old.setExtractorVersion(2);
        old.setOcrAvailable(true);
        org.apache.lucene.document.Document entry = DocumentIndex.toLucene(old);
        entry.removeFields(IndexFields.ENTITY);
        entry.removeFields(IndexFields.DATA_TYPE);
        entry.removeFields(IndexFields.ENTITIES_VERSION);
        try (org.apache.lucene.store.FSDirectory dir = org.apache.lucene.store.FSDirectory.open(location);
             org.apache.lucene.index.IndexWriter writer = new org.apache.lucene.index.IndexWriter(dir,
                     new org.apache.lucene.index.IndexWriterConfig(new SpanishTextAnalyzer()))) {
            writer.addDocument(entry);
        }

        try (DocumentIndex index = DocumentIndex.openForWriting(location)) {
            assertThat(paths(index.search(withData("dni:12345678Z", List.of())))).isEmpty();

            assertThat(index.refreshEntities(Path.of("/exp"), () -> false)).isEqualTo(1);

            assertThat(paths(index.search(withData("dni:12345678Z", List.of()))))
                    .containsExactly("/exp/perez/demanda.pdf");
            FileState state = index.fileStates(null).get("/exp/perez/demanda.pdf");
            assertThat(state.extractorVersion()).isEqualTo(2);
            assertThat(state.ocrAvailable()).isTrue();
            assertThat(state.hasText()).isTrue();
            assertThat(index.preview("/exp/perez/demanda.pdf", null).text()).isEqualTo("Cliente con DNI 12345678Z.");
            assertThat(index.refreshEntities(Path.of("/exp"), () -> false)).isZero();
        }
    }
}
