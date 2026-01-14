package com.allende.filesearch.model;

/**
 * Represents index statistics.
 */
public class IndexStats {
    private long documentCount;
    private long indexSizeBytes;
    private double indexSizeMB;
    private String indexName;
    private String health;
    private int numberOfShards;
    private int numberOfReplicas;

    public IndexStats() {
    }

    public long getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(long documentCount) {
        this.documentCount = documentCount;
    }

    public long getIndexSizeBytes() {
        return indexSizeBytes;
    }

    public void setIndexSizeBytes(long indexSizeBytes) {
        this.indexSizeBytes = indexSizeBytes;
        this.indexSizeMB = indexSizeBytes / (1024.0 * 1024.0);
    }

    public double getIndexSizeMB() {
        return indexSizeMB;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getHealth() {
        return health;
    }

    public void setHealth(String health) {
        this.health = health;
    }

    public int getNumberOfShards() {
        return numberOfShards;
    }

    public void setNumberOfShards(int numberOfShards) {
        this.numberOfShards = numberOfShards;
    }

    public int getNumberOfReplicas() {
        return numberOfReplicas;
    }

    public void setNumberOfReplicas(int numberOfReplicas) {
        this.numberOfReplicas = numberOfReplicas;
    }

    @Override
    public String toString() {
        return String.format(
                "Index: %s | Documents: %d | Size: %.2f MB | Health: %s | Shards: %d | Replicas: %d",
                indexName, documentCount, indexSizeMB, health, numberOfShards, numberOfReplicas);
    }
}
