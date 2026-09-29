package com.allende.filesearch.index;

import com.allende.filesearch.model.Document;

import java.util.List;

/**
 * Text of one document for the preview.
 *
 * @param document     indexed metadata (without content)
 * @param text         the document text with matches between the highlight markers
 * @param truncated    true when only the first {@link DocumentIndex#PREVIEW_MAX_CHARS} characters are shown
 * @param personalData personal data found in {@code text}, in text order
 */
public record DocumentPreview(Document document, String text, boolean truncated, List<PersonalData> personalData) {

    /** Characters [start, end) of the text hold personal data of this kind (an EntityType key). */
    public record PersonalData(int start, int end, String type) {
    }
}
