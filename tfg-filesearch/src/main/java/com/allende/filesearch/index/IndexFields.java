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

    private IndexFields() {
    }
}
