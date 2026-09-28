import { describe, expect, it } from 'vitest';
import { isLoopbackHttpUrl, isValidIndexName } from './esTarget';

describe('isLoopbackHttpUrl', () => {
    it.each([
        'http://localhost:9200',
        'http://localhost:9200/',
        'https://127.0.0.1:9200',
        'http://[::1]:9200',
    ])('accepts %s', (url) => {
        expect(isLoopbackHttpUrl(url)).toBe(true);
    });

    it.each([
        'http://192.168.1.10:9200',
        'http://es.example.com',
        'http://localhost.example.com:9200',
        'http://user:pass@localhost:9200',
        'http://localhost:9200/other-path',
        'file:///etc/passwd',
        'ftp://localhost',
        'not a url',
        '',
        42,
        null,
    ])('rejects %s', (url) => {
        expect(isLoopbackHttpUrl(url)).toBe(false);
    });
});

describe('isValidIndexName', () => {
    it('accepts Elasticsearch-style index names', () => {
        expect(isValidIndexName('filesearch')).toBe(true);
        expect(isValidIndexName('despacho-2026_v2')).toBe(true);
    });

    it('rejects names that could change the request path', () => {
        for (const name of ['', '_all', '-x', 'Upper', 'a/b', '../x', 'a b', '*', 'a?x=1', 'x'.repeat(256)]) {
            expect(isValidIndexName(name)).toBe(false);
        }
    });
});
