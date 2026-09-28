package com.allende.filesearch.index;

/** What the index knows about a file, used to detect changes without re-reading it. */
public record FileState(long size, long modifiedAtMillis) {
}
