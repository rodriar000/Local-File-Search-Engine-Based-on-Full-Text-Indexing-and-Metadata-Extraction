/**
 * Input validation for everything the renderer can ask the main process to do.
 *
 * The renderer displays untrusted document content, so the main process treats
 * every IPC argument as hostile. These functions are pure so they can be unit tested.
 */
import path from 'node:path';
/** Document types the indexer handles; only these may be opened from a search result. */
export const OPENABLE_EXTENSIONS: ReadonlySet<string> = new Set([
    'txt', 'pdf', 'docx', 'doc', 'html', 'htm', 'xml', 'rtf', 'odt',
    'md', 'json', 'csv', 'pptx', 'ppt', 'xlsx', 'xls',
    'msg', 'eml', 'zip', 'tif', 'tiff', 'jpg', 'jpeg', 'png',
]);

export const MAX_API_BODY_BYTES = 64 * 1024;

export type ApiMethod = 'GET' | 'POST';

export interface ApiRequest {
    method: ApiMethod;
    /** Path on the local backend, e.g. `/api/search`. */
    path: string;
    body?: unknown;
}

/** Everything the UI may ask the backend; the list is exhaustive. */
const API_ROUTES: Readonly<Record<string, ApiMethod>> = {
    '/api/health': 'GET',
    '/api/stats': 'GET',
    '/api/search': 'POST',
    '/api/preview': 'POST',
    '/api/report': 'POST',
    '/api/index': 'POST',
    '/api/index/status': 'GET',
    '/api/index/cancel': 'POST',
};

const ROUTES_WITH_BODY: ReadonlySet<string> = new Set(['/api/search', '/api/preview', '/api/report', '/api/index']);

export function isAllowedApiRequest(request: unknown): request is ApiRequest {
    if (typeof request !== 'object' || request === null) return false;
    const { method, path: reqPath, body } = request as Record<string, unknown>;
    if (typeof method !== 'string' || typeof reqPath !== 'string') return false;
    if (!Object.prototype.hasOwnProperty.call(API_ROUTES, reqPath) || API_ROUTES[reqPath] !== method) return false;

    if (!ROUTES_WITH_BODY.has(reqPath)) return body === undefined;
    if (typeof body !== 'object' || body === null || Array.isArray(body)) return false;
    if (JSON.stringify(body).length > MAX_API_BODY_BYTES) return false;
    if (reqPath === '/api/index') return isValidIndexFolder((body as Record<string, unknown>).folder);
    if (reqPath === '/api/preview') {
        const { path: docPath, query } = body as Record<string, unknown>;
        return typeof docPath === 'string' && docPath.length > 0 && docPath.length <= 4096
            && (query === undefined || typeof query === 'string');
    }
    if (reqPath === '/api/report') {
        const { name, identifier } = body as Record<string, unknown>;
        const optionalText = (value: unknown, max: number) => value === undefined || (typeof value === 'string' && value.length <= max);
        return optionalText(name, 200) && optionalText(identifier, 100) && (name !== undefined || identifier !== undefined);
    }
    return true;
}

/**
 * A search result may only open an existing regular file of a document type,
 * never an executable, script or shortcut.
 */
export function isOpenableDocumentPath(filePath: unknown): filePath is string {
    if (typeof filePath !== 'string' || filePath.length === 0 || filePath.length > 4096) return false;
    if (filePath.includes('\0') || !path.isAbsolute(filePath)) return false;
    if (/^[a-z][a-z0-9+.-]*:\/\//i.test(filePath)) return false; // URLs, not paths
    const ext = path.extname(filePath).slice(1).toLowerCase();
    return OPENABLE_EXTENSIONS.has(ext);
}

/** Folder chosen for indexing: an absolute local path, no option injection into the CLI. */
export function isValidIndexFolder(folder: unknown): folder is string {
    return typeof folder === 'string'
        && folder.length > 0
        && folder.length <= 4096
        && !folder.includes('\0')
        && !folder.startsWith('-')
        && path.isAbsolute(folder);
}
