package com.allende.filesearch.model;

public class IndexingStats {
    private java.util.List<IndexingRun> history = new java.util.ArrayList<>();

    public java.util.List<IndexingRun> getHistory() {
        return history;
    }

    public void setHistory(java.util.List<IndexingRun> history) {
        this.history = history;
    }

    // Helper to get latest run for backward compatibility logic if needed
    @com.fasterxml.jackson.annotation.JsonIgnore
    public IndexingRun getLatest() {
        if (history.isEmpty())
            return null;
        return history.get(history.size() - 1);
    }
}
