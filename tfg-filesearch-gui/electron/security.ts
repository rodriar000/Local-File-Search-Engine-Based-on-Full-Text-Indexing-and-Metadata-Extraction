/**
 * Input validation for everything the renderer can ask the main process to do.
 *
 * The renderer displays untrusted document content, so the main process treats
 * every IPC argument as hostile. These functions are pure so they can be unit tested.
 */
import path from 'node:path';
import { isLoopbackHttpUrl, isValidIndexName } from '../src/shared/esTarget';

export { isLoopbackHttpUrl, isValidIndexName };

/** Document types the indexer handles; only these may be opened from a search result. */
export const OPENABLE_EXTENSIONS: ReadonlySet<string> = new Set([
    'txt', 'pdf', 'docx', 'doc', 'html', 'htm', 'xml', 'rtf', 'odt',
    'md', 'json', 'csv', 'pptx', 'ppt', 'xlsx', 'xls',
]);

export const MAX_ES_BODY_BYTES = 64 * 1024;

export type EsMethod = 'GET' | 'HEAD' | 'POST';

export interface EsRequest {
    method: EsMethod;
    /** Path relative to the Elasticsearch base URL, e.g. `/filesearch/_search`. */
    path: string;
    body?: unknown;
}

export interface EsTarget {
    url: string;
    indexName: string;
}

/**
 * Read-only operations the UI needs. Anything else (deleting the index,
 * cluster settings, other indices) is refused.
 */
export function isAllowedEsRequest(request: unknown, indexName: string): request is EsRequest {
    if (typeof request !== 'object' || request === null) return false;
    const { method, path: reqPath, body } = request as Record<string, unknown>;
    if (typeof method !== 'string' || typeof reqPath !== 'string') return false;

    const allowed: Record<string, EsMethod> = {
        '/': 'GET',
        [`/${indexName}`]: 'HEAD',
        [`/${indexName}/_stats`]: 'GET',
        [`/${indexName}/_search`]: 'POST',
    };
    if (allowed[reqPath] !== method) return false;

    if (method === 'POST') {
        if (typeof body !== 'object' || body === null || Array.isArray(body)) return false;
        return JSON.stringify(body).length <= MAX_ES_BODY_BYTES;
    }
    return body === undefined;
}

export function isValidEsTarget(target: unknown): target is EsTarget {
    if (typeof target !== 'object' || target === null) return false;
    const { url, indexName } = target as Record<string, unknown>;
    return isLoopbackHttpUrl(url) && isValidIndexName(indexName);
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
