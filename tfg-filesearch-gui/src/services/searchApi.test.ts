import { describe, expect, it } from 'vitest';
import { toSearchBody, toSearchResult } from './searchApi';

describe('toSearchBody', () => {
    it('converts sizes in MB and whole local days', () => {
        const body = toSearchBody('fianza', {
            extensions: ['pdf'],
            sizeMin: 1,
            sizeMax: 2.5,
            dateFrom: '2026-01-01',
            dateTo: '2026-01-31',
        }, 20, 20);

        expect(body).toEqual({
            query: 'fianza',
            extensions: ['pdf'],
            sizeMinBytes: 1024 * 1024,
            sizeMaxBytes: 2.5 * 1024 * 1024,
            modifiedFrom: new Date(2026, 0, 1, 0, 0, 0, 0).toISOString(),
            modifiedTo: new Date(2026, 0, 31, 23, 59, 59, 999).toISOString(),
            from: 20,
            size: 20,
        });
    });

    it('leaves out filters that are not set or invalid', () => {
        const body = toSearchBody('', { extensions: [], dateFrom: 'not a date' }, 0, 10);
        expect(body.sizeMinBytes).toBeUndefined();
        expect(body.modifiedFrom).toBeUndefined();
        expect(JSON.parse(JSON.stringify(body))).toEqual({ query: '', extensions: [], from: 0, size: 10 });
    });
});

describe('toSearchResult', () => {
    it('maps backend hits to what the result list shows', () => {
        const result = toSearchResult({
            totalHits: 2,
            tookMs: 4,
            hits: [
                {
                    path: '/exp/demanda.pdf', filename: 'demanda.pdf', extension: 'pdf', size: 10,
                    modifiedAt: '2026-01-01T10:00:00Z', score: 1.5, snippet: 'la fianza',
                },
                { path: '/exp/escaneado.pdf', filename: 'escaneado.pdf', extension: 'pdf', size: 20, score: 1 },
            ],
        }, 'fianza', { extensions: [] }, 12);

        expect(result.totalHits).toBe(2);
        expect(result.metrics?.execution.totalTimeMs).toBe(12);
        expect(result.hits[0].document).toMatchObject({ path: '/exp/demanda.pdf', modified_at: '2026-01-01T10:00:00Z' });
        expect(result.hits[0].highlight).toEqual({ content: ['la fianza'] });
        expect(result.hits[1].highlight).toBeUndefined();
    });
});
