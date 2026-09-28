package com.allende.filesearch.index;

import com.allende.filesearch.model.Document;

/**
 * Text of one document for the preview.
 *
 * @param document  indexed metadata (without content)
 * @param text      the document text with matches between the highlight markers
 * @param truncated true when only the first {@link DocumentIndex#PREVIEW_MAX_CHARS} characters are shown
 */
public record DocumentPreview(Document document, String text, boolean truncated) {
}
