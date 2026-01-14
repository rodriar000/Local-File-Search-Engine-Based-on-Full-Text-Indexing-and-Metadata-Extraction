export interface Document {
    path: string;
    filename: string;
    extension: string;
    size: number;
    created_at?: string;
    modified_at?: string;
    content?: string;
    author?: string;
    title?: string;
}

export interface SearchMetrics {
    params: {
        query: string;
        filters: SearchFilters;
    };
    execution: {
        totalTimeMs: number;
        elasticTookMs: number;
        timestamp: string;
    };
}

export interface SearchResult {
    totalHits: number;
    hits: {
        score: number;
        document: Document;
        highlight?: Record<string, string[]>;
    }[];
    took: number;
    metrics?: SearchMetrics;
}

export interface SearchFilters {
    extensions: string[];
    sizeMin?: number;
    sizeMax?: number;
    dateFrom?: string;
    dateTo?: string;
}

export interface AppConfig {
    elasticsearch: {
        url: string;
        indexName: string;
    };
}

export interface SearchHistoryPoint {
    timestamp: string; // ISO date
    avgTimeMs: number;
}

export interface SearchAnalytics {
    totalSearches: number;
    totalSearchTimeMs: number;
    termFrequency: Record<string, number>;
    history: SearchHistoryPoint[];
}

export interface IndexingRun {
    timestamp: string;
    durationMs: number;
    totalDocuments: number;
    docsPerSecond: number;
    totalBytes: number;
}

export interface IndexingAnalytics {
    history: IndexingRun[];
    // Aggregate/Latest stats for display purposes
    totalDocuments: number;
    sizeBytes: number;
    lastRun: string;
    durationMs: number;
    docsPerSecond: number;
}

export interface SystemAnalytics {
    search: SearchAnalytics | null;
    indexing: IndexingAnalytics | null;
}
export interface IndexStats {
    documentCount: number;
    sizeInBytes: number;
    health?: string;
    fileTypes?: Record<string, number>;
}
