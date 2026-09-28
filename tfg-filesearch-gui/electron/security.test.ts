import { describe, expect, it } from 'vitest';
import {
    MAX_ES_BODY_BYTES,
    isAllowedEsRequest,
    isOpenableDocumentPath,
    isValidEsTarget,
    isValidIndexFolder,
} from './security';

describe('isAllowedEsRequest', () => {
    const index = 'filesearch';

    it('allows the read-only operations the UI uses', () => {
        expect(isAllowedEsRequest({ method: 'GET', path: '/' }, index)).toBe(true);
        expect(isAllowedEsRequest({ method: 'HEAD', path: '/filesearch' }, index)).toBe(true);
        expect(isAllowedEsRequest({ method: 'GET', path: '/filesearch/_stats' }, index)).toBe(true);
        expect(isAllowedEsRequest({ method: 'POST', path: '/filesearch/_search', body: { query: {} } }, index)).toBe(true);
    });

    it('refuses destructive or unrelated requests', () => {
        expect(isAllowedEsRequest({ method: 'DELETE', path: '/filesearch' }, index)).toBe(false);
        expect(isAllowedEsRequest({ method: 'POST', path: '/filesearch/_delete_by_query', body: {} }, index)).toBe(false);
        expect(isAllowedEsRequest({ method: 'POST', path: '/other/_search', body: {} }, index)).toBe(false);
        expect(isAllowedEsRequest({ method: 'GET', path: '/_cat/indices' }, index)).toBe(false);
        expect(isAllowedEsRequest({ method: 'POST', path: '/filesearch/_search?scroll=1m', body: {} }, index)).toBe(false);
        expect(isAllowedEsRequest({ method: 'GET', path: 'constructor' }, index)).toBe(false);
    });

    it('validates the request body', () => {
        expect(isAllowedEsRequest({ method: 'POST', path: '/filesearch/_search' }, index)).toBe(false);
        expect(isAllowedEsRequest({ method: 'POST', path: '/filesearch/_search', body: [] }, index)).toBe(false);
        expect(isAllowedEsRequest({ method: 'GET', path: '/', body: {} }, index)).toBe(false);
        const huge = { query: 'x'.repeat(MAX_ES_BODY_BYTES) };
        expect(isAllowedEsRequest({ method: 'POST', path: '/filesearch/_search', body: huge }, index)).toBe(false);
    });

    it('rejects malformed input', () => {
        for (const request of [null, undefined, 'GET /', { method: 'GET' }, { path: '/' }]) {
            expect(isAllowedEsRequest(request, index)).toBe(false);
        }
    });
});

describe('isValidEsTarget', () => {
    it('requires a loopback URL and a valid index name', () => {
        expect(isValidEsTarget({ url: 'http://localhost:9200', indexName: 'filesearch' })).toBe(true);
        expect(isValidEsTarget({ url: 'http://10.0.0.5:9200', indexName: 'filesearch' })).toBe(false);
        expect(isValidEsTarget({ url: 'http://localhost:9200', indexName: '../x' })).toBe(false);
        expect(isValidEsTarget(null)).toBe(false);
    });
});

describe('isOpenableDocumentPath', () => {
    it('accepts absolute paths to supported documents', () => {
        expect(isOpenableDocumentPath('/home/ana/Expedientes/Garcia/demanda.pdf')).toBe(true);
        expect(isOpenableDocumentPath('/home/ana/escrito.DOCX')).toBe(true);
    });

    it('refuses executables, scripts and shortcuts', () => {
        for (const file of ['/tmp/x.exe', '/tmp/x.bat', '/tmp/x.sh', '/tmp/x.app', '/tmp/x.lnk', '/tmp/x.jar', '/tmp/x.pdf.exe', '/tmp/noextension']) {
            expect(isOpenableDocumentPath(file)).toBe(false);
        }
    });

    it('refuses relative paths, URLs and malformed input', () => {
        for (const file of ['demanda.pdf', '../demanda.pdf', 'https://evil.example/x.pdf', 'file:///tmp/x.pdf', '/tmp/a\0.pdf', '', 7, null]) {
            expect(isOpenableDocumentPath(file)).toBe(false);
        }
    });
});

describe('isValidIndexFolder', () => {
    it('accepts absolute folders and refuses option-like or relative input', () => {
        expect(isValidIndexFolder('/home/ana/Expedientes')).toBe(true);
        expect(isValidIndexFolder('Expedientes')).toBe(false);
        expect(isValidIndexFolder('--help')).toBe(false);
        expect(isValidIndexFolder('/tmp/a\0b')).toBe(false);
        expect(isValidIndexFolder(undefined)).toBe(false);
    });
});
