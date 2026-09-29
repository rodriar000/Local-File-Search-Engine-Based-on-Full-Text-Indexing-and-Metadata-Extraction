package com.allende.filesearch.index;

/** Field names of the Lucene index. */
public final class IndexFields {
    public static final String PATH = "path";
    public static final String FILENAME = "filename";
    public static final String EXTENSION = "extension";
    public static final String SIZE = "size";
    public static final String CREATED_AT = "created_at";
    public static final String MODIFIED_AT = "modified_at";
    public static final String CHECKSUM = "checksum_sha256";
    public static final String CONTENT = "content";
    public static final String LANGUAGE = "language";
    public static final String AUTHOR = "author";
    public static final String TITLE = "title";
    public static final String LAST_INDEXED_AT = "last_indexed_at";
    /** Doc values: 1 when text was extracted. */
    public static final String HAS_TEXT = "has_text";
    /** Doc values: 1 when OCR was available at extraction time. */
    public static final String OCR_AVAILABLE = "ocr_available";
    /** Doc values: DocumentExtractor.VERSION that produced the entry. */
    public static final String EXTRACTOR_VERSION = "extractor_version";
    /** Identifiers found in the text, as "type:value" terms (see EntityExtractor). */
    public static final String ENTITY = "entity";
    /** Stored: kinds of data found in the text (EntityType keys). */
    public static final String DATA_TYPE = "data_type";
    /** Doc values: EntityExtractor.VERSION that found the entities. */
    public static final String ENTITIES_VERSION = "entities_version";

    private IndexFields() {
    }
}
