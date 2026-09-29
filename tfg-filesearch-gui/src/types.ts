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
    /** Kinds of data found in the document (see shared/personalData.ts). */
    dataTypes?: string[];
}

export interface SearchMetrics {
    params: {
        query: string;
        filters: SearchFilters;
    };
    execution: {
        totalTimeMs: number;
        engineTookMs: number;
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
    /** Only documents containing this DNI, NIE, CIF, IBAN, phone, e-mail or case number. */
    identifier?: string;
    /** Only documents containing any of these kinds of data. */
    dataTypes?: string[];
}

/** A document's text for the preview, with matches between highlight markers. */
export interface DocumentPreviewData {
    path: string;
    filename: string;
    extension: string;
    size: number;
    modifiedAt?: string;
    title?: string;
    author?: string;
    text: string;
    /** Only the beginning of a very long document is shown. */
    truncated: boolean;
    /** Personal data in `text`. */
    personalData?: import('./shared/personalData').PersonalDataSpan[];
}

export interface SyncReport {
    root: string;
    scanned: number;
    added: number;
    updated: number;
    unchanged: number;
    deleted: number;
    skippedTooLarge: number;
    /** Indexed by name only because no text could be extracted (e.g. scanned PDFs). */
    withoutText: number;
    failed: number;
    failures: { path: string; reason: string }[];
    durationMs: number;
    cancelled: boolean;
}

/** Progress of the current indexing run, or the outcome of the last one. */
export interface IndexStatus {
    running: boolean;
    folder?: string;
    processed: number;
    total: number;
    startedAt?: string;
    finishedAt?: string;
    lastReport?: SyncReport;
    error?: string;
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

/** Licence status from the main process (see electron/license.ts). */
export interface LicenseDetails {
    id: string;
    customer: string;
    seats: number;
    issuedAt: string;
    expiresAt: string | null;
}

export type LicenseState =
    | { kind: 'trial'; daysLeft: number; endsAt: string }
    | { kind: 'trial-ended'; endedAt: string }
    | { kind: 'licensed'; details: LicenseDetails; daysLeft: number | null }
    | { kind: 'expired'; details: LicenseDetails };

export interface LicenseInstallResult {
    installed: boolean;
    error?: string;
    state: LicenseState;
}
