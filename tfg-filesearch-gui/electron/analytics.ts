/**
 * Converts the analytics files written by the Java backend (~/.filesearch/*.json)
 * into the shape the dashboard expects. The files are external input: missing,
 * partial or older-format files must yield null or zeros, never crash the UI.
 */
import type { IndexingAnalytics, IndexingRun, SearchAnalytics } from '../src/types';

const num = (value: unknown): number => (typeof value === 'number' && Number.isFinite(value) ? value : 0);

/** Jackson writes Instants as epoch seconds (with fraction) by default; accept ISO strings too. */
function toIsoTimestamp(value: unknown): string | null {
    if (typeof value === 'number' && Number.isFinite(value)) {
        return new Date(value * 1000).toISOString();
    }
    if (typeof value === 'string' && !Number.isNaN(Date.parse(value))) {
        return new Date(value).toISOString();
    }
    return null;
}

function toIndexingRun(raw: unknown): IndexingRun | null {
    if (typeof raw !== 'object' || raw === null) return null;
    const run = raw as Record<string, unknown>;
    const timestamp = toIsoTimestamp(run.timestamp);
    if (!timestamp) return null;
    return {
        timestamp,
        durationMs: num(run.durationMs),
        totalDocuments: num(run.totalDocuments),
        docsPerSecond: num(run.docsPerSecond),
        totalBytes: num(run.totalBytes),
    };
}

export function normalizeIndexingStats(raw: unknown): IndexingAnalytics | null {
    if (typeof raw !== 'object' || raw === null) return null;
    const rawHistory = (raw as Record<string, unknown>).history;
    const history = (Array.isArray(rawHistory) ? rawHistory : [])
        .map(toIndexingRun)
        .filter((run): run is IndexingRun => run !== null);
    const latest = history[history.length - 1];
    if (!latest) return null;
    return {
        history,
        totalDocuments: latest.totalDocuments,
        sizeBytes: latest.totalBytes,
        lastRun: latest.timestamp,
        durationMs: latest.durationMs,
        docsPerSecond: latest.docsPerSecond,
    };
}

/** Search terms stay on disk: the dashboard only needs counts and timings. */
export function normalizeSearchStats(raw: unknown): SearchAnalytics | null {
    if (typeof raw !== 'object' || raw === null) return null;
    const stats = raw as Record<string, unknown>;
    const history = (Array.isArray(stats.history) ? stats.history : [])
        .map((point: unknown) => {
            const p = (point ?? {}) as Record<string, unknown>;
            const timestamp = toIsoTimestamp(p.timestamp);
            return timestamp ? { timestamp, avgTimeMs: num(p.avgTimeMs) } : null;
        })
        .filter((point): point is { timestamp: string; avgTimeMs: number } => point !== null);
    return {
        totalSearches: num(stats.totalSearches),
        totalSearchTimeMs: num(stats.totalSearchTimeMs),
        termFrequency: {},
        history,
    };
}
