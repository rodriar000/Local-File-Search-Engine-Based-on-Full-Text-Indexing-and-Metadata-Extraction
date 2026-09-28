package com.allende.filesearch.index;

import java.util.Map;

/**
 * Overview of the index for the dashboard and the doctor command.
 *
 * @param exists        false until something has been indexed
 * @param documentCount indexed documents
 * @param sizeBytes     disk space used by the index
 * @param fileTypes     document count per file extension
 */
public record IndexSummary(boolean exists, long documentCount, long sizeBytes, Map<String, Long> fileTypes) {
}
