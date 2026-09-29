package com.allende.filesearch.index;

import com.allende.filesearch.entities.EntityExtractor;
import com.allende.filesearch.entities.EntityType;
import com.allende.filesearch.model.Document;
import com.allende.filesearch.model.SearchResult;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.uhighlight.DefaultPassageFormatter;
import org.apache.lucene.search.uhighlight.LengthGoalBreakIterator;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.search.uhighlight.WholeBreakIterator;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

/**
 * Embedded full-text index of the user's documents (Apache Lucene), stored in a
 * local folder. Nothing listens on the network.
 *
 * <p>Open it for writing from a single process (the indexer or the desktop
 * app's backend); any number of read-only instances may search at the same time
 * and see the last committed state.
 */
public final class DocumentIndex implements Closeable {

    /** Highlight markers (Unicode private use); clients render them, never HTML. */
    public static final String HIGHLIGHT_PRE = "";
    public static final String HIGHLIGHT_POST = "";

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_FROM = 10_000;
    private static final int SNIPPET_LENGTH = 160;
    /** Characters of a document shown in the preview. */
    public static final int PREVIEW_MAX_CHARS = 300_000;
    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private static final Map<String, Float> QUERY_FIELD_WEIGHTS = Map.of(
            IndexFields.CONTENT, 1.0f,
            IndexFields.TITLE, 1.5f,
            IndexFields.FILENAME, 2.0f,
            IndexFields.AUTHOR, 1.0f);

    private static final Set<String> RESULT_FIELDS = Set.of(
            IndexFields.PATH, IndexFields.FILENAME, IndexFields.EXTENSION, IndexFields.SIZE,
            IndexFields.CREATED_AT, IndexFields.MODIFIED_AT, IndexFields.CHECKSUM, IndexFields.LANGUAGE,
            IndexFields.AUTHOR, IndexFields.TITLE, IndexFields.LAST_INDEXED_AT, IndexFields.DATA_TYPE);

    /** Most documents listed in one RGPD report. */
    public static final int MAX_REPORT_DOCUMENTS = 10_000;
    /** Personal data marked in one preview. */
    static final int MAX_PREVIEW_SPANS = 5_000;
    /** Rewritten entries between commits while updating the entities of an older index. */
    private static final int REFRESH_COMMIT_EVERY = 200;

    /** Content is stored (for snippets) and indexed with offsets (for fast highlighting). */
    private static final FieldType CONTENT_TYPE;

    static {
        FieldType type = new FieldType(TextField.TYPE_STORED);
        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        type.freeze();
        CONTENT_TYPE = type;
    }

    private final Path location;
    private final FSDirectory directory;
    private final Analyzer analyzer;
    private final IndexWriter writer;
    private SearcherManager searcherManager;

    private DocumentIndex(Path location, FSDirectory directory, Analyzer analyzer,
                          IndexWriter writer, SearcherManager searcherManager) {
        this.location = location;
        this.directory = directory;
        this.analyzer = analyzer;
        this.writer = writer;
        this.searcherManager = searcherManager;
    }

    /** Opens (creating if needed) the index for indexing and searching. */
    public static DocumentIndex openForWriting(Path location) throws IOException {
        Files.createDirectories(location);
        FSDirectory directory = FSDirectory.open(location);
        Analyzer analyzer = new SpanishTextAnalyzer();
        IndexWriter writer;
        try {
            IndexWriterConfig config = new IndexWriterConfig(analyzer)
                    .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            writer = new IndexWriter(directory, config);
        } catch (LockObtainFailedException e) {
            directory.close();
            analyzer.close();
            throw new IndexLockedException(location, e);
        }
        // Make sure a commit point exists so read-only instances can open the index.
        writer.commit();
        return new DocumentIndex(location, directory, analyzer, writer, new SearcherManager(writer, null));
    }

    /** Opens the index for searching only. A missing index behaves as an empty one. */
    public static DocumentIndex openReadOnly(Path location) throws IOException {
        FSDirectory directory = FSDirectory.open(location);
        SearcherManager manager = DirectoryReader.indexExists(directory) ? new SearcherManager(directory, null) : null;
        return new DocumentIndex(location, directory, new SpanishTextAnalyzer(), null, manager);
    }

    public Path getLocation() {
        return location;
    }

    // ------------------------------------------------------------------ writing

    /** Adds the document, replacing any previous version with the same path. */
    public void upsert(Document doc) throws IOException {
        requireWriter().updateDocument(new Term(IndexFields.PATH, doc.getPath()), toLucene(doc));
    }

    public void delete(String path) throws IOException {
        requireWriter().deleteDocuments(new Term(IndexFields.PATH, path));
    }

    public void deleteAll() throws IOException {
        requireWriter().deleteAll();
    }

    /** Makes changes durable and visible to searches. */
    public void commit() throws IOException {
        requireWriter().commit();
        searcherManager.maybeRefreshBlocking();
    }

    /**
     * Returns the size and modification time of every indexed file under {@code root}
     * (or all files when root is null), keyed by path.
     */
    public Map<String, FileState> fileStates(Path root) throws IOException {
        Map<String, FileState> states = new HashMap<>();
        if (searcherManager == null) {
            return states;
        }
        String prefix = root == null ? null : withTrailingSeparator(root.toAbsolutePath().normalize().toString());
        IndexSearcher searcher = searcherManager.acquire();
        try {
            for (LeafReaderContext leaf : searcher.getIndexReader().leaves()) {
                LeafReader reader = leaf.reader();
                Bits live = reader.getLiveDocs();
                StoredFields stored = reader.storedFields();
                NumericDocValues sizes = DocValues.getNumeric(reader, IndexFields.SIZE);
                NumericDocValues modified = DocValues.getNumeric(reader, IndexFields.MODIFIED_AT);
                NumericDocValues versions = DocValues.getNumeric(reader, IndexFields.EXTRACTOR_VERSION);
                NumericDocValues hasText = DocValues.getNumeric(reader, IndexFields.HAS_TEXT);
                NumericDocValues ocr = DocValues.getNumeric(reader, IndexFields.OCR_AVAILABLE);
                for (int doc = 0; doc < reader.maxDoc(); doc++) {
                    if (live != null && !live.get(doc)) {
                        continue;
                    }
                    String path = stored.document(doc, Set.of(IndexFields.PATH)).get(IndexFields.PATH);
                    if (path == null || (prefix != null && !path.startsWith(prefix))) {
                        continue;
                    }
                    long size = sizes.advanceExact(doc) ? sizes.longValue() : -1;
                    long mtime = modified.advanceExact(doc) ? modified.longValue() : -1;
                    // Entries written before these values existed read as version 0, so they are re-extracted.
                    int version = versions.advanceExact(doc) ? (int) versions.longValue() : 0;
                    boolean text = hasText.advanceExact(doc) && hasText.longValue() == 1;
                    boolean ocrUsed = ocr.advanceExact(doc) && ocr.longValue() == 1;
                    states.put(path, new FileState(size, mtime, version, text, ocrUsed));
                }
            }
        } finally {
            searcherManager.release(searcher);
        }
        return states;
    }

    // ---------------------------------------------------------------- searching

    public SearchResult search(SearchRequest request) throws IOException {
        long start = System.nanoTime();
        refreshReadOnlyView();
        if (searcherManager == null) {
            return emptyResult(start);
        }

        Query query = buildQuery(request);
        int size = Math.max(1, Math.min(request.size(), MAX_PAGE_SIZE));
        int from = Math.max(0, Math.min(request.from(), MAX_FROM));

        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocs top = searcher.search(query, from + size);
            long totalHits = searcher.count(query);
            ScoreDoc[] page = from >= top.scoreDocs.length
                    ? new ScoreDoc[0]
                    : Arrays.copyOfRange(top.scoreDocs, from, top.scoreDocs.length);

            String[] snippets = page.length == 0 ? new String[0] : highlighter(searcher)
                    .highlight(IndexFields.CONTENT, query, new TopDocs(new TotalHits(page.length, TotalHits.Relation.EQUAL_TO), page), 1);

            StoredFields stored = searcher.storedFields();
            List<SearchResult.DocumentHit> hits = new ArrayList<>(page.length);
            double maxScore = 0;
            for (int i = 0; i < page.length; i++) {
                Document doc = fromLucene(stored.document(page[i].doc, RESULT_FIELDS));
                SearchResult.DocumentHit hit = new SearchResult.DocumentHit(doc, page[i].score);
                String snippet = snippets[i];
                if (snippet != null && !snippet.isEmpty()) {
                    doc.setContent(snippet);
                    hit.setHighlights(List.of(snippet));
                }
                hits.add(hit);
                maxScore = Math.max(maxScore, page[i].score);
            }

            long tookMs = (System.nanoTime() - start) / 1_000_000;
            SearchResult result = new SearchResult(totalHits, tookMs, hits);
            result.setMaxScore(maxScore);
            result.setTotalTimeMs(tookMs);
            result.setResultsPerSecond(tookMs > 0 ? hits.size() / (tookMs / 1000.0) : hits.size());
            return result;
        } finally {
            searcherManager.release(searcher);
        }
    }

    /**
     * The text of one indexed document for the preview, with every match of
     * {@code query} marked. Only indexed documents can be previewed.
     *
     * @return null when the path is not in the index
     */
    public DocumentPreview preview(String path, String query) throws IOException {
        refreshReadOnlyView();
        if (searcherManager == null || path == null) {
            return null;
        }
        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocs found = searcher.search(new TermQuery(new Term(IndexFields.PATH, path)), 1);
            if (found.scoreDocs.length == 0) {
                return null;
            }
            ScoreDoc hit = found.scoreDocs[0];
            org.apache.lucene.document.Document stored = searcher.storedFields().document(hit.doc);
            Document doc = fromLucene(stored);
            String content = nullToEmpty(stored.get(IndexFields.CONTENT));
            boolean truncated = content.length() > PREVIEW_MAX_CHARS;

            String text = null;
            if (query != null && !query.isBlank()) {
                Query parsed = buildQuery(SearchRequest.of(query, 1));
                TopDocs single = new TopDocs(new TotalHits(1, TotalHits.Relation.EQUAL_TO), new ScoreDoc[] { hit });
                text = previewHighlighter(searcher).highlight(IndexFields.CONTENT, parsed, single, 1)[0];
            }
            if (text == null || text.isEmpty()) {
                text = truncated ? content.substring(0, PREVIEW_MAX_CHARS) : content;
            }
            return new DocumentPreview(doc, text, truncated, personalData(text));
        } finally {
            searcherManager.release(searcher);
        }
    }

    /** One document of an RGPD report and why it is listed. */
    public record ReportEntry(Document document, boolean byName, boolean byIdentifier) {
    }

    /**
     * @param total     documents that mention the person
     * @param truncated true when more than {@link #MAX_REPORT_DOCUMENTS} did and only those are listed
     */
    public record Report(long total, boolean truncated, List<ReportEntry> entries) {
    }

    /**
     * Every document that mentions a person, for a data subject's access or
     * erasure request: those containing the exact name and those containing the
     * identifier. At least one of them must be given. Listed by path.
     *
     * @param name       full name, searched as an exact phrase (ignoring case and accents), or null
     * @param entityTerm identifier as an index term such as "dni:12345678Z", or null
     */
    public Report report(String name, String entityTerm) throws IOException {
        refreshReadOnlyView();
        boolean hasName = name != null && !name.isBlank();
        boolean hasIdentifier = entityTerm != null && !entityTerm.isBlank();
        if (!hasName && !hasIdentifier) {
            throw new IllegalArgumentException("name or identifier required");
        }
        if (searcherManager == null) {
            return new Report(0, false, List.of());
        }
        Query byName = new MatchNoDocsQuery();
        if (hasName) {
            SimpleQueryParser parser = new SimpleQueryParser(analyzer, QUERY_FIELD_WEIGHTS);
            Query parsed = parser.parse("\"" + name.replace('"', ' ').strip() + "\"");
            byName = parsed != null ? parsed : byName;
        }
        Query byIdentifier = hasIdentifier ? new TermQuery(new Term(IndexFields.ENTITY, entityTerm)) : new MatchNoDocsQuery();
        Query either = new BooleanQuery.Builder()
                .add(byName, BooleanClause.Occur.SHOULD)
                .add(byIdentifier, BooleanClause.Occur.SHOULD)
                .build();

        IndexSearcher searcher = searcherManager.acquire();
        try {
            Set<Integer> nameDocs = docIds(searcher, byName);
            Set<Integer> identifierDocs = docIds(searcher, byIdentifier);
            long total = searcher.count(either);
            Set<Integer> all = new java.util.TreeSet<>(nameDocs);
            all.addAll(identifierDocs);
            StoredFields stored = searcher.storedFields();
            List<ReportEntry> entries = new ArrayList<>(all.size());
            for (int doc : all) {
                entries.add(new ReportEntry(fromLucene(stored.document(doc, RESULT_FIELDS)),
                        nameDocs.contains(doc), identifierDocs.contains(doc)));
            }
            entries.sort(Comparator.comparing(entry -> entry.document().getPath()));
            if (entries.size() > MAX_REPORT_DOCUMENTS) {
                entries = new ArrayList<>(entries.subList(0, MAX_REPORT_DOCUMENTS));
            }
            return new Report(total, total > entries.size(), entries);
        } finally {
            searcherManager.release(searcher);
        }
    }

    private static Set<Integer> docIds(IndexSearcher searcher, Query query) throws IOException {
        Set<Integer> ids = new java.util.HashSet<>();
        for (ScoreDoc hit : searcher.search(query, MAX_REPORT_DOCUMENTS).scoreDocs) {
            ids.add(hit.doc);
        }
        return ids;
    }

    /**
     * Personal data in a preview text (DNI, IBAN, phones, health words…), as
     * positions in that same text, which may contain highlight markers.
     */
    static List<DocumentPreview.PersonalData> personalData(String text) {
        StringBuilder plain = new StringBuilder(text.length());
        int[] original = new int[text.length() + 1];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == HIGHLIGHT_PRE.charAt(0) || c == HIGHLIGHT_POST.charAt(0)) {
                continue;
            }
            original[plain.length()] = i;
            plain.append(c);
        }
        List<DocumentPreview.PersonalData> spans = new ArrayList<>();
        for (EntityExtractor.Entity entity : EntityExtractor.find(plain.toString())) {
            if (!entity.type().personal()) {
                continue;
            }
            spans.add(new DocumentPreview.PersonalData(original[entity.start()], original[entity.end() - 1] + 1,
                    entity.type().key()));
            if (spans.size() >= MAX_PREVIEW_SPANS) {
                break;
            }
        }
        return spans;
    }

    private record StaleEntry(String path, boolean ocrAvailable, int extractorVersion) {
    }

    /**
     * Finds identifiers again in entries under {@code root} analysed by an older
     * {@link EntityExtractor}, from the text already in the index, so documents
     * do not have to be read (or OCR'd) again.
     *
     * @return number of entries updated
     */
    public int refreshEntities(Path root, BooleanSupplier cancelled) throws IOException {
        refreshReadOnlyView();
        if (searcherManager == null) {
            return 0;
        }
        String prefix = root == null ? null : withTrailingSeparator(root.toAbsolutePath().normalize().toString());
        List<StaleEntry> stale = new ArrayList<>();
        IndexSearcher searcher = searcherManager.acquire();
        try {
            for (LeafReaderContext leaf : searcher.getIndexReader().leaves()) {
                LeafReader reader = leaf.reader();
                Bits live = reader.getLiveDocs();
                StoredFields stored = reader.storedFields();
                NumericDocValues entities = DocValues.getNumeric(reader, IndexFields.ENTITIES_VERSION);
                NumericDocValues versions = DocValues.getNumeric(reader, IndexFields.EXTRACTOR_VERSION);
                NumericDocValues ocr = DocValues.getNumeric(reader, IndexFields.OCR_AVAILABLE);
                for (int doc = 0; doc < reader.maxDoc(); doc++) {
                    if ((live != null && !live.get(doc))
                            || (entities.advanceExact(doc) && entities.longValue() >= EntityExtractor.VERSION)) {
                        continue;
                    }
                    String path = stored.document(doc, Set.of(IndexFields.PATH)).get(IndexFields.PATH);
                    if (path == null || (prefix != null && !path.startsWith(prefix))) {
                        continue;
                    }
                    int version = versions.advanceExact(doc) ? (int) versions.longValue() : 0;
                    stale.add(new StaleEntry(path, ocr.advanceExact(doc) && ocr.longValue() == 1, version));
                }
            }
        } finally {
            searcherManager.release(searcher);
        }

        int updated = 0;
        for (StaleEntry entry : stale) {
            if (cancelled.getAsBoolean()) {
                break;
            }
            Document doc = storedDocument(entry.path());
            if (doc == null) {
                continue;
            }
            doc.setOcrAvailable(entry.ocrAvailable());
            doc.setExtractorVersion(entry.extractorVersion());
            upsert(doc);
            if (++updated % REFRESH_COMMIT_EVERY == 0) {
                commit();
            }
        }
        if (updated > 0) {
            commit();
        }
        return updated;
    }

    /** An indexed entry with its text, as last committed. */
    private Document storedDocument(String path) throws IOException {
        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocs found = searcher.search(new TermQuery(new Term(IndexFields.PATH, path)), 1);
            if (found.scoreDocs.length == 0) {
                return null;
            }
            org.apache.lucene.document.Document stored = searcher.storedFields().document(found.scoreDocs[0].doc);
            Document doc = fromLucene(stored);
            doc.setContent(stored.get(IndexFields.CONTENT));
            return doc;
        } finally {
            searcherManager.release(searcher);
        }
    }

    public IndexSummary summary() throws IOException {
        refreshReadOnlyView();
        if (searcherManager == null) {
            return new IndexSummary(false, 0, 0, Map.of());
        }
        IndexSearcher searcher = searcherManager.acquire();
        try {
            Map<String, Long> fileTypes = new TreeMap<>();
            for (LeafReaderContext leaf : searcher.getIndexReader().leaves()) {
                LeafReader reader = leaf.reader();
                Bits live = reader.getLiveDocs();
                SortedDocValues extensions = DocValues.getSorted(reader, IndexFields.EXTENSION);
                long[] counts = new long[extensions.getValueCount()];
                for (int doc = extensions.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = extensions.nextDoc()) {
                    if (live == null || live.get(doc)) {
                        counts[extensions.ordValue()]++;
                    }
                }
                for (int ord = 0; ord < counts.length; ord++) {
                    if (counts[ord] > 0) {
                        fileTypes.merge(extensions.lookupOrd(ord).utf8ToString(), counts[ord], Long::sum);
                    }
                }
            }
            return new IndexSummary(true, searcher.getIndexReader().numDocs(), directorySizeBytes(), fileTypes);
        } finally {
            searcherManager.release(searcher);
        }
    }

    Query buildQuery(SearchRequest request) {
        Query text;
        if (request.query().isBlank()) {
            text = new MatchAllDocsQuery();
        } else {
            SimpleQueryParser parser = new SimpleQueryParser(analyzer, QUERY_FIELD_WEIGHTS);
            parser.setDefaultOperator(BooleanClause.Occur.MUST);
            Query parsed = parser.parse(request.query());
            // Null when the query only contained stop words or operators.
            text = parsed != null ? parsed : new MatchNoDocsQuery();
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder().add(text, BooleanClause.Occur.MUST);
        if (!request.extensions().isEmpty()) {
            List<BytesRef> terms = request.extensions().stream()
                    .map(ext -> new BytesRef(ext.toLowerCase(Locale.ROOT)))
                    .toList();
            builder.add(new TermInSetQuery(IndexFields.EXTENSION, terms), BooleanClause.Occur.FILTER);
        }
        if (request.sizeMinBytes() != null || request.sizeMaxBytes() != null) {
            builder.add(LongPoint.newRangeQuery(IndexFields.SIZE,
                    request.sizeMinBytes() != null ? request.sizeMinBytes() : Long.MIN_VALUE,
                    request.sizeMaxBytes() != null ? request.sizeMaxBytes() : Long.MAX_VALUE), BooleanClause.Occur.FILTER);
        }
        if (request.modifiedFrom() != null || request.modifiedTo() != null) {
            builder.add(LongPoint.newRangeQuery(IndexFields.MODIFIED_AT,
                    request.modifiedFrom() != null ? request.modifiedFrom().toEpochMilli() : Long.MIN_VALUE,
                    request.modifiedTo() != null ? request.modifiedTo().toEpochMilli() : Long.MAX_VALUE), BooleanClause.Occur.FILTER);
        }
        if (request.entity() != null) {
            builder.add(new TermQuery(new Term(IndexFields.ENTITY, request.entity())), BooleanClause.Occur.FILTER);
        }
        if (!request.dataTypes().isEmpty()) {
            List<BytesRef> types = request.dataTypes().stream().map(BytesRef::new).toList();
            builder.add(new TermInSetQuery(IndexFields.DATA_TYPE, types), BooleanClause.Occur.FILTER);
        }
        return builder.build();
    }

    private UnifiedHighlighter highlighter(IndexSearcher searcher) {
        return UnifiedHighlighter.builder(searcher, analyzer)
                .withFormatter(new DefaultPassageFormatter(HIGHLIGHT_PRE, HIGHLIGHT_POST, "… ", false))
                .withBreakIterator(() -> LengthGoalBreakIterator.createClosestToLength(
                        BreakIterator.getSentenceInstance(SPANISH), SNIPPET_LENGTH, 0.5f))
                // Highlight matches anywhere in the document, not only in its first 10,000 characters.
                .withMaxLength(Integer.MAX_VALUE - 1)
                .build();
    }

    /** Marks every match in the first PREVIEW_MAX_CHARS characters as one passage. */
    private UnifiedHighlighter previewHighlighter(IndexSearcher searcher) {
        return UnifiedHighlighter.builder(searcher, analyzer)
                .withFormatter(new DefaultPassageFormatter(HIGHLIGHT_PRE, HIGHLIGHT_POST, "", false))
                .withBreakIterator(WholeBreakIterator::new)
                .withMaxLength(PREVIEW_MAX_CHARS)
                .withHighlightPhrasesStrictly(true)
                .build();
    }

    /** Read-only instances pick up commits made by the writing process. */
    private void refreshReadOnlyView() throws IOException {
        if (writer != null) {
            searcherManager.maybeRefresh();
            return;
        }
        if (searcherManager == null) {
            if (DirectoryReader.indexExists(directory)) {
                searcherManager = new SearcherManager(directory, null);
            }
        } else {
            searcherManager.maybeRefresh();
        }
    }

    private SearchResult emptyResult(long start) {
        SearchResult result = new SearchResult(0, (System.nanoTime() - start) / 1_000_000, List.of());
        result.setTotalTimeMs(result.getTookMs());
        return result;
    }

    private long directorySizeBytes() throws IOException {
        long total = 0;
        for (String file : directory.listAll()) {
            try {
                total += directory.fileLength(file);
            } catch (NoSuchFileException e) {
                // Removed by a concurrent merge; not part of the index any more.
            }
        }
        return total;
    }

    private IndexWriter requireWriter() {
        if (writer == null) {
            throw new IllegalStateException("The index was opened read-only");
        }
        return writer;
    }

    // ------------------------------------------------------------- conversions

    /**
     * The tokenizer keeps "contrato_final.pdf" as a single word, so split file
     * names on the usual separators to make "contrato" find it.
     */
    static String searchableName(String filename) {
        return filename.replaceAll("[._\\-]+", " ");
    }

    static org.apache.lucene.document.Document toLucene(Document doc) {
        org.apache.lucene.document.Document out = new org.apache.lucene.document.Document();
        out.add(new StringField(IndexFields.PATH, doc.getPath(), Field.Store.YES));
        String filename = nullToEmpty(doc.getFilename());
        out.add(new StoredField(IndexFields.FILENAME, filename));
        out.add(new TextField(IndexFields.FILENAME, searchableName(filename), Field.Store.NO));

        String extension = nullToEmpty(doc.getExtension()).toLowerCase(Locale.ROOT);
        out.add(new StringField(IndexFields.EXTENSION, extension, Field.Store.YES));
        out.add(new SortedDocValuesField(IndexFields.EXTENSION, new BytesRef(extension)));

        addLong(out, IndexFields.SIZE, doc.getSize());
        addLong(out, IndexFields.MODIFIED_AT, doc.getModifiedAt() != null ? doc.getModifiedAt().toEpochMilli() : 0);
        if (doc.getCreatedAt() != null) {
            out.add(new StoredField(IndexFields.CREATED_AT, doc.getCreatedAt().toEpochMilli()));
        }
        if (doc.getLastIndexedAt() != null) {
            out.add(new StoredField(IndexFields.LAST_INDEXED_AT, doc.getLastIndexedAt().toEpochMilli()));
        }
        if (doc.getChecksumSha256() != null) {
            out.add(new StoredField(IndexFields.CHECKSUM, doc.getChecksumSha256()));
        }
        if (doc.getLanguage() != null) {
            out.add(new StoredField(IndexFields.LANGUAGE, doc.getLanguage()));
        }
        if (doc.getTitle() != null) {
            out.add(new TextField(IndexFields.TITLE, doc.getTitle(), Field.Store.YES));
        }
        if (doc.getAuthor() != null) {
            out.add(new TextField(IndexFields.AUTHOR, doc.getAuthor(), Field.Store.YES));
        }
        out.add(new Field(IndexFields.CONTENT, nullToEmpty(doc.getContent()), CONTENT_TYPE));
        boolean hasText = doc.getContent() != null && !doc.getContent().isBlank();
        out.add(new NumericDocValuesField(IndexFields.HAS_TEXT, hasText ? 1 : 0));
        out.add(new NumericDocValuesField(IndexFields.OCR_AVAILABLE, doc.isOcrAvailable() ? 1 : 0));
        out.add(new NumericDocValuesField(IndexFields.EXTRACTOR_VERSION, doc.getExtractorVersion()));

        List<EntityExtractor.Entity> entities = EntityExtractor.find(entityText(doc));
        for (String term : EntityExtractor.terms(entities)) {
            out.add(new StringField(IndexFields.ENTITY, term, Field.Store.NO));
        }
        EnumSet<EntityType> types = EnumSet.noneOf(EntityType.class);
        entities.forEach(entity -> types.add(entity.type()));
        for (EntityType type : types) {
            out.add(new StringField(IndexFields.DATA_TYPE, type.key(), Field.Store.YES));
        }
        out.add(new NumericDocValuesField(IndexFields.ENTITIES_VERSION, EntityExtractor.VERSION));
        return out;
    }

    static Document fromLucene(org.apache.lucene.document.Document stored) {
        Document doc = new Document();
        doc.setPath(stored.get(IndexFields.PATH));
        doc.setFilename(stored.get(IndexFields.FILENAME));
        doc.setExtension(stored.get(IndexFields.EXTENSION));
        doc.setSize(longValue(stored, IndexFields.SIZE, 0));
        doc.setModifiedAt(instant(stored, IndexFields.MODIFIED_AT));
        doc.setCreatedAt(instant(stored, IndexFields.CREATED_AT));
        doc.setLastIndexedAt(instant(stored, IndexFields.LAST_INDEXED_AT));
        doc.setChecksumSha256(stored.get(IndexFields.CHECKSUM));
        doc.setLanguage(stored.get(IndexFields.LANGUAGE));
        doc.setTitle(stored.get(IndexFields.TITLE));
        doc.setAuthor(stored.get(IndexFields.AUTHOR));
        doc.setDataTypes(List.of(stored.getValues(IndexFields.DATA_TYPE)));
        return doc;
    }

    private static String entityText(Document doc) {
        String content = nullToEmpty(doc.getContent());
        return doc.getTitle() == null ? content : doc.getTitle() + "\n" + content;
    }

    private static void addLong(org.apache.lucene.document.Document out, String name, long value) {
        out.add(new LongPoint(name, value));
        out.add(new NumericDocValuesField(name, value));
        out.add(new StoredField(name, value));
    }

    private static long longValue(org.apache.lucene.document.Document stored, String name, long fallback) {
        IndexableField field = stored.getField(name);
        return field != null && field.numericValue() != null ? field.numericValue().longValue() : fallback;
    }

    private static Instant instant(org.apache.lucene.document.Document stored, String name) {
        IndexableField field = stored.getField(name);
        return field != null && field.numericValue() != null ? Instant.ofEpochMilli(field.numericValue().longValue()) : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String withTrailingSeparator(String path) {
        String separator = java.io.File.separator;
        return path.endsWith(separator) ? path : path + separator;
    }

    @Override
    public void close() throws IOException {
        try {
            if (searcherManager != null) {
                searcherManager.close();
            }
            if (writer != null) {
                writer.close();
            }
        } finally {
            directory.close();
            analyzer.close();
        }
    }
}
