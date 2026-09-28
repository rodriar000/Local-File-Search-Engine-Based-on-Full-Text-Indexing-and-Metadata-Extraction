package com.allende.filesearch.index;

import java.io.IOException;
import java.nio.file.Path;

/** Another process (usually the desktop app) is writing to the index. */
public class IndexLockedException extends IOException {
    public IndexLockedException(Path directory, Throwable cause) {
        super("The index at " + directory + " is in use by another process (is the desktop app running?). "
                + "Close it or use the app to update the index.", cause);
    }
}
