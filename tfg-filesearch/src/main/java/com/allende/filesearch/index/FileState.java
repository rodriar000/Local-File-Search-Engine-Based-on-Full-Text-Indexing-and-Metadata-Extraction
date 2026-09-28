package com.allende.filesearch.index;

/**
 * What the index knows about a file, used to detect changes without re-reading it.
 *
 * @param extractorVersion version of the text extraction that produced the entry
 * @param hasText          whether any text was extracted
 * @param ocrAvailable     whether OCR could be used at the time
 */
public record FileState(long size, long modifiedAtMillis, int extractorVersion, boolean hasText, boolean ocrAvailable) {

    /** Same file as when it was indexed. */
    public boolean sameFileAs(long otherSize, long otherModifiedAtMillis) {
        return size == otherSize && modifiedAtMillis == otherModifiedAtMillis;
    }
}
