package com.allende.filesearch.model;

import java.util.List;

/**
 * Represents search results returned from Elasticsearch.
 */
public class SearchResult {
    private long totalHits;
    private double maxScore;
    private long tookMs; // Elasticsearch execution time
    private long totalTimeMs; // Total server-side execution time
    private double resultsPerSecond;
    private List<DocumentHit> hits;

    public SearchResult() {
    }

    public SearchResult(long totalHits, long tookMs, List<DocumentHit> hits) {
        this.totalHits = totalHits;
        this.tookMs = tookMs;
        this.hits = hits;
    }

    public long getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(long totalHits) {
        this.totalHits = totalHits;
    }

    public double getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(double maxScore) {
        this.maxScore = maxScore;
    }

    public long getTookMs() {
        return tookMs;
    }

    public void setTookMs(long tookMs) {
        this.tookMs = tookMs;
    }

    public long getTotalTimeMs() {
        return totalTimeMs;
    }

    public void setTotalTimeMs(long totalTimeMs) {
        this.totalTimeMs = totalTimeMs;
    }

    public double getResultsPerSecond() {
        return resultsPerSecond;
    }

    public void setResultsPerSecond(double resultsPerSecond) {
        this.resultsPerSecond = resultsPerSecond;
    }

    public List<DocumentHit> getHits() {
        return hits;
    }

    public void setHits(List<DocumentHit> hits) {
        this.hits = hits;
    }

    public static class DocumentHit {
        private Document document;
        private double score;
        private List<String> highlights;

        public DocumentHit(Document document, double score) {
            this.document = document;
            this.score = score;
        }

        public Document getDocument() {
            return document;
        }

        public void setDocument(Document document) {
            this.document = document;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        public List<String> getHighlights() {
            return highlights;
        }

        public void setHighlights(List<String> highlights) {
            this.highlights = highlights;
        }
    }
}
