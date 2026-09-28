import { describe, expect, it } from 'vitest';
import { normalizeIndexingStats, normalizeSearchStats } from './analytics';

describe('normalizeIndexingStats', () => {
    it('reads the format written by the Java backend', () => {
        const raw = {
            history: [
                { timestamp: 1790610000.5, durationMs: 100, totalDocuments: 3, docsPerSecond: 30, totalBytes: 900 },
                { timestamp: 1790610905.629116155, durationMs: 85, totalDocuments: 1, docsPerSecond: 11.76, totalBytes: 59 },
            ],
        };
        expect(normalizeIndexingStats(raw)).toEqual({
            history: [
                { timestamp: '2026-09-28T15:40:00.500Z', durationMs: 100, totalDocuments: 3, docsPerSecond: 30, totalBytes: 900 },
                { timestamp: '2026-09-28T15:55:05.629Z', durationMs: 85, totalDocuments: 1, docsPerSecond: 11.76, totalBytes: 59 },
            ],
            totalDocuments: 1,
            sizeBytes: 59,
            lastRun: '2026-09-28T15:55:05.629Z',
            durationMs: 85,
            docsPerSecond: 11.76,
        });
    });

    it('accepts ISO timestamps and fills missing numbers with zero', () => {
        const result = normalizeIndexingStats({ history: [{ timestamp: '2026-01-02T03:04:05Z' }] });
        expect(result?.lastRun).toBe('2026-01-02T03:04:05.000Z');
        expect(result?.docsPerSecond).toBe(0);
    });

    it('returns null for missing, empty or malformed files', () => {
        for (const raw of [null, undefined, 'x', {}, { history: [] }, { history: 'nope' }, { history: [{ durationMs: 5 }] }]) {
            expect(normalizeIndexingStats(raw)).toBeNull();
        }
    });
});

describe('normalizeSearchStats', () => {
    it('keeps counts and timings but never exposes search terms', () => {
        const result = normalizeSearchStats({
            totalSearches: 4,
            totalSearchTimeMs: 80,
            termFrequency: { 'garcía lópez': 3 },
            history: [{ timestamp: 1790610000, avgTimeMs: 20 }, { bad: true }],
        });
        expect(result).toEqual({
            totalSearches: 4,
            totalSearchTimeMs: 80,
            termFrequency: {},
            history: [{ timestamp: '2026-09-28T15:40:00.000Z', avgTimeMs: 20 }],
        });
    });

    it('returns null when there is no file', () => {
        expect(normalizeSearchStats(null)).toBeNull();
    });
});
