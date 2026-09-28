package com.allende.filesearch.index;

import com.allende.filesearch.index.IndexSynchronizer.SyncReport;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.tika.DocumentExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class IndexSynchronizerTest {

    @TempDir
    Path tempDir;

    private Path docs;
    private DocumentIndex index;
    private IndexSynchronizer synchronizer;

    @BeforeEach
    void setUp() throws Exception {
        docs = Files.createDirectories(tempDir.resolve("expedientes"));
        Config config = new Config();
        config.getIndexing().setExcludePatterns(List.of("**/.git/**", "**/~$*"));
        config.getIndexing().setThreads(2);
        index = DocumentIndex.openForWriting(tempDir.resolve("index"));
        synchronizer = new IndexSynchronizer(index, new DocumentExtractor(), config);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        index.close();
    }

    private SyncReport sync() throws Exception {
        return synchronizer.sync(docs, IndexSynchronizer.ProgressListener.NONE, new AtomicBoolean(false), null);
    }

    private long hits(String query) throws Exception {
        return index.search(SearchRequest.of(query, 10)).getTotalHits();
    }

    private Path write(String relative, String text) throws Exception {
        Path file = docs.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    @Test
    void indexesOnlyNewAndChangedFilesAndRemovesDeletedOnes() throws Exception {
        Path demanda = write("garcia/demanda.txt", "Demanda de desahucio por falta de pago");
        Path notas = write("lopez/notas.txt", "Notas sobre la fianza del contrato");

        SyncReport first = sync();
        assertThat(first.added()).isEqualTo(2);
        assertThat(hits("desahucio")).isEqualTo(1);

        SyncReport unchanged = sync();
        assertThat(unchanged.added()).isZero();
        assertThat(unchanged.updated()).isZero();
        assertThat(unchanged.unchanged()).isEqualTo(2);

        Files.writeString(notas, "Notas sobre la cláusula suelo y la hipoteca");
        Files.setLastModifiedTime(notas, FileTime.from(Instant.now().plusSeconds(5)));
        Files.delete(demanda);

        SyncReport second = sync();
        assertThat(second.updated()).isEqualTo(1);
        assertThat(second.deleted()).isEqualTo(1);
        assertThat(hits("desahucio")).isZero();
        assertThat(hits("fianza")).isZero();
        assertThat(hits("hipoteca")).isEqualTo(1);
    }

    @Test
    void treatsARenameAsRemoveAndAdd() throws Exception {
        Path original = write("garcia/borrador.txt", "Recurso de apelación");
        sync();

        Files.move(original, docs.resolve("garcia/recurso-final.txt"));
        SyncReport report = sync();

        assertThat(report.added()).isEqualTo(1);
        assertThat(report.deleted()).isEqualTo(1);
        assertThat(index.search(SearchRequest.of("apelacion", 10)).getHits())
                .extracting(h -> h.getDocument().getFilename())
                .containsExactly("recurso-final.txt");
    }

    @Test
    void skipsExcludedFoldersLockFilesAndUnsupportedTypes() throws Exception {
        write("garcia/.git/config.txt", "secreto de repositorio");
        write("garcia/~$demanda.txt", "archivo de bloqueo de Office");
        write("garcia/programa.exe", "binario");
        write("garcia/demanda.txt", "texto válido");

        SyncReport report = sync();

        assertThat(report.scanned()).isEqualTo(1);
        assertThat(hits("repositorio")).isZero();
        assertThat(hits("bloqueo")).isZero();
        assertThat(hits("valido")).isEqualTo(1);
    }

    @Test
    void onlyRemovesDocumentsUnderTheSyncedFolder() throws Exception {
        write("garcia/demanda.txt", "desahucio");
        Path otherFolder = Files.createDirectories(tempDir.resolve("otros"));
        Files.writeString(otherFolder.resolve("nota.txt"), "fianza");

        sync();
        synchronizer.sync(otherFolder, IndexSynchronizer.ProgressListener.NONE, new AtomicBoolean(false), null);
        SyncReport again = sync();

        assertThat(again.deleted()).isZero();
        assertThat(hits("fianza")).isEqualTo(1);
    }

    @Test
    void indexesEmptyFilesByNameAndCountsThem() throws Exception {
        Files.createDirectories(docs);
        Files.createFile(docs.resolve("escaneado.pdf"));

        SyncReport report = sync();

        assertThat(report.withoutText()).isEqualTo(1);
        assertThat(hits("escaneado")).isEqualTo(1);
    }

    @Test
    void reportsUnreadableFilesButKeepsThemFindableByName() throws Exception {
        write("roto.pdf", "%PDF-1.7\n1 0 obj << /Type /Catalog /Pages 9 0 R >> endobj\n%%truncated");

        SyncReport report = sync();

        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.failures().get(0).reason()).contains("Could not read");
        assertThat(report.withoutText()).isZero();
        assertThat(hits("roto")).isEqualTo(1);
    }

    @Test
    void reExtractsEntriesFromOlderExtractorVersions() throws Exception {
        Path file = write("demanda.txt", "Demanda de desahucio");
        sync();
        com.allende.filesearch.model.Document old = new DocumentExtractor().extractDocument(file);
        old.setExtractorVersion(DocumentExtractor.VERSION - 1);
        index.upsert(old);
        index.commit();

        SyncReport report = sync();

        assertThat(report.updated()).isEqualTo(1);
        assertThat(sync().unchanged()).isEqualTo(1);
    }

    @Test
    void cancellationKeepsExistingEntries() throws Exception {
        write("a.txt", "primero");
        sync();
        Files.delete(docs.resolve("a.txt"));
        write("b.txt", "segundo");

        SyncReport report = synchronizer.sync(docs, IndexSynchronizer.ProgressListener.NONE, new AtomicBoolean(true), null);

        assertThat(report.cancelled()).isTrue();
        assertThat(report.deleted()).isZero();
        assertThat(hits("primero")).isEqualTo(1);
    }

    @Test
    void syncFileUpdatesAndRemovesSingleFiles() throws Exception {
        Path file = write("nota.txt", "embargo preventivo");
        synchronizer.syncFile(file);
        index.commit();
        assertThat(hits("embargo")).isEqualTo(1);

        Files.delete(file);
        synchronizer.syncFile(file);
        index.commit();
        assertThat(hits("embargo")).isZero();
    }
}
