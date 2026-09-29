import { describe, expect, it } from 'vitest';
import {
    MAX_API_BODY_BYTES,
    isAllowedApiRequest,
    isOpenableDocumentPath,
    isValidIndexFolder,
} from './security';

describe('isAllowedApiRequest', () => {
    it('allows the operations the UI uses', () => {
        expect(isAllowedApiRequest({ method: 'GET', path: '/api/health' })).toBe(true);
        expect(isAllowedApiRequest({ method: 'GET', path: '/api/stats' })).toBe(true);
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/search', body: { query: 'fianza' } })).toBe(true);
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/index', body: { folder: '/home/ana/Expedientes' } })).toBe(true);
        expect(isAllowedApiRequest({ method: 'GET', path: '/api/index/status' })).toBe(true);
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/index/cancel' })).toBe(true);
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/preview', body: { path: '/exp/a.pdf', query: 'fianza' } })).toBe(true);
    });

    it('refuses unknown routes and wrong methods', () => {
        expect(isAllowedApiRequest({ method: 'GET', path: '/api/search' })).toBe(false);
        expect(isAllowedApiRequest({ method: 'DELETE', path: '/api/index' })).toBe(false);
        expect(isAllowedApiRequest({ method: 'GET', path: '/api/stats?x=1' })).toBe(false);
        expect(isAllowedApiRequest({ method: 'GET', path: '/api/../api/stats' })).toBe(false);
        expect(isAllowedApiRequest({ method: 'GET', path: 'constructor' })).toBe(false);
        expect(isAllowedApiRequest({ method: 'GET', path: 'toString' })).toBe(false);
    });

    it('validates the request body', () => {
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/search' })).toBe(false);
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/search', body: [] })).toBe(false);
        expect(isAllowedApiRequest({ method: 'GET', path: '/api/stats', body: {} })).toBe(false);
        const huge = { query: 'x'.repeat(MAX_API_BODY_BYTES) };
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/search', body: huge })).toBe(false);
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/index', body: { folder: 'relative' } })).toBe(false);
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/index', body: {} })).toBe(false);
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/preview', body: {} })).toBe(false);
        expect(isAllowedApiRequest({ method: 'POST', path: '/api/preview', body: { path: '/a.pdf', query: 1 } })).toBe(false);
    });

    it('allows data protection reports by name and/or identifier only', () => {
        const report = (body: unknown) => isAllowedApiRequest({ method: 'POST', path: '/api/report', body });
        expect(report({ name: 'Juan Pérez García' })).toBe(true);
        expect(report({ identifier: '12345678Z' })).toBe(true);
        expect(report({ name: 'Juan Pérez', identifier: '12345678Z' })).toBe(true);
        expect(report({})).toBe(false);
        expect(report({ name: 42 })).toBe(false);
        expect(report({ name: 'x'.repeat(201) })).toBe(false);
        expect(report({ identifier: 'x'.repeat(101) })).toBe(false);
        expect(isAllowedApiRequest({ method: 'GET', path: '/api/report' })).toBe(false);
    });

    it('rejects malformed input', () => {
        for (const request of [null, undefined, 'GET /api/stats', { method: 'GET' }, { path: '/api/stats' }]) {
            expect(isAllowedApiRequest(request)).toBe(false);
        }
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
