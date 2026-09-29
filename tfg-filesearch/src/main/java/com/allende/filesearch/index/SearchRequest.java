package com.allende.filesearch.index;

import java.time.Instant;
import java.util.List;

/**
 * A search over the index.
 *
 * @param query      user query in simple syntax: words (all required), "exact phrase",
 *                   "phrase"~N for proximity, -exclude, a | b for either, prefix*;
 *                   blank matches every document
 * @param extensions only these file types; empty means all
 * @param sizeMinBytes minimum file size, or null
 * @param sizeMaxBytes maximum file size, or null
 * @param modifiedFrom earliest modification date, or null
 * @param modifiedTo   latest modification date, or null
 * @param entity       only documents containing this identifier, as an index term such as
 *                     "dni:12345678Z" (see EntityExtractor), or null
 * @param dataTypes    only documents containing any of these kinds of data (EntityType keys); empty means all
 */
public record SearchRequest(
        String query,
        List<String> extensions,
        Long sizeMinBytes,
        Long sizeMaxBytes,
        Instant modifiedFrom,
        Instant modifiedTo,
        String entity,
        List<String> dataTypes,
        int from,
        int size) {

    public SearchRequest {
        query = query == null ? "" : query;
        extensions = extensions == null ? List.of() : List.copyOf(extensions);
        dataTypes = dataTypes == null ? List.of() : List.copyOf(dataTypes);
    }

    public SearchRequest(String query, List<String> extensions, Long sizeMinBytes, Long sizeMaxBytes,
                         Instant modifiedFrom, Instant modifiedTo, int from, int size) {
        this(query, extensions, sizeMinBytes, sizeMaxBytes, modifiedFrom, modifiedTo, null, List.of(), from, size);
    }

    public static SearchRequest of(String query, int size) {
        return new SearchRequest(query, List.of(), null, null, null, null, 0, size);
    }

    /** Whether any filter other than the text query is set. */
    public boolean hasFilters() {
        return !extensions.isEmpty() || sizeMinBytes != null || sizeMaxBytes != null || modifiedFrom != null
                || modifiedTo != null || entity != null || !dataTypes.isEmpty();
    }
}
