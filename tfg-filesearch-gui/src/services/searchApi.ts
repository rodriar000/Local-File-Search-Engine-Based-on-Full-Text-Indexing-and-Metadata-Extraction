import { DocumentPreviewData, IndexStats, IndexStatus, SearchFilters, SearchResult } from '../types';

/**
 * Client for the local search backend. Requests go through the main process,
 * which starts the backend, holds its token and only forwards allowed calls.
 */

export class SearchEngineUnavailableError extends Error {
    constructor(reason?: string) {
        super(reason ?? 'La búsqueda necesita la aplicación de escritorio.');
        this.name = 'SearchEngineUnavailableError';
    }
}

export class IndexMissingError extends Error {
    constructor() {
        super('Aún no hay documentos indexados. Elige la carpeta de documentos en Configuración.');
        this.name = 'IndexMissingError';
    }
}

export class IndexingAlreadyRunningError extends Error {
    constructor() {
        super('Ya se está actualizando el índice.');
        this.name = 'IndexingAlreadyRunningError';
    }
}

type Method = 'GET' | 'POST';

/** Electron prefixes errors thrown by the main process with the IPC channel; users only need the reason. */
export function ipcErrorMessage(error: unknown): string {
    const message = error instanceof Error ? error.message : String(error);
    return message.replace(/^Error invoking remote method '[^']*': (?:Error: )?/, '');
}

async function apiRequest<T>(method: Method, path: string, body?: unknown): Promise<{ status: number; data: T }> {
    if (!window.electronAPI) throw new SearchEngineUnavailableError();
    let response: ApiBridgeResponse;
    try {
        response = await window.electronAPI.apiRequest({ method, path, body });
    } catch (error) {
        throw new Error(ipcErrorMessage(error));
    }
    if (response.status === 0) throw new SearchEngineUnavailableError(response.error);
    return { status: response.status, data: response.data as T };
}

function assertOk(status: number, data: unknown, operation: string) {
    if (status >= 200 && status < 300) return;
    const detail = typeof data === 'object' && data !== null && 'error' in data ? `: ${String((data as { error: unknown }).error)}` : '';
    throw new Error(`No se pudo ${operation} (HTTP ${status}${detail}).`);
}

// ------------------------------------------------------------------ search

const MB = 1024 * 1024;

export interface SearchBody {
    query: string;
    extensions: string[];
    sizeMinBytes?: number;
    sizeMaxBytes?: number;
    modifiedFrom?: string;
    modifiedTo?: string;
    from: number;
    size: number;
}

/** Date inputs give "YYYY-MM-DD" in local time; the "to" date includes that whole day. */
function dayStart(date: string): string | undefined {
    const parsed = new Date(`${date}T00:00:00.000`);
    return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
}

function dayEnd(date: string): string | undefined {
    const parsed = new Date(`${date}T23:59:59.999`);
    return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
}

export function toSearchBody(query: string, filters: SearchFilters, from: number, size: number): SearchBody {
    return {
        query,
        extensions: filters.extensions,
        sizeMinBytes: filters.sizeMin !== undefined ? Math.round(filters.sizeMin * MB) : undefined,
        sizeMaxBytes: filters.sizeMax !== undefined ? Math.round(filters.sizeMax * MB) : undefined,
        modifiedFrom: filters.dateFrom ? dayStart(filters.dateFrom) : undefined,
        modifiedTo: filters.dateTo ? dayEnd(filters.dateTo) : undefined,
        from,
        size,
    };
}

export interface ApiHit {
    path: string;
    filename: string;
    extension: string;
    size: number;
    modifiedAt?: string;
    createdAt?: string;
    title?: string;
    author?: string;
    score: number;
    snippet?: string;
}

export interface ApiSearchResponse {
    totalHits: number;
    tookMs: number;
    hits: ApiHit[];
}

export function toSearchResult(response: ApiSearchResponse, query: string, filters: SearchFilters, totalTimeMs: number): SearchResult {
    return {
        totalHits: response.totalHits,
        took: response.tookMs,
        metrics: {
            params: { query, filters },
            execution: { totalTimeMs, engineTookMs: response.tookMs, timestamp: new Date().toISOString() },
        },
        hits: response.hits.map((hit) => ({
            score: hit.score,
            document: {
                path: hit.path,
                filename: hit.filename,
                extension: hit.extension,
                size: hit.size,
                modified_at: hit.modifiedAt,
                created_at: hit.createdAt,
                title: hit.title,
                author: hit.author,
            },
            highlight: hit.snippet ? { content: [hit.snippet] } : undefined,
        })),
    };
}

export async function search(query: string, filters: SearchFilters, from = 0, size = 20): Promise<SearchResult> {
    const startTime = performance.now();
    const { status, data } = await apiRequest<ApiSearchResponse>('POST', '/api/search', toSearchBody(query, filters, from, size));
    assertOk(status, data, 'buscar');
    return toSearchResult(data, query, filters, performance.now() - startTime);
}

export async function getPreview(path: string, query: string): Promise<DocumentPreviewData> {
    const { status, data } = await apiRequest<DocumentPreviewData>('POST', '/api/preview', { path, query });
    if (status === 404) throw new Error('Este documento ya no está en el índice. Actualiza el índice en Configuración.');
    assertOk(status, data, 'cargar la vista previa');
    return data;
}

// ------------------------------------------------------------- index info

interface ApiIndexSummary {
    exists: boolean;
    documentCount: number;
    sizeBytes: number;
    fileTypes: Record<string, number>;
}

export async function getStats(): Promise<IndexStats> {
    const { status, data } = await apiRequest<ApiIndexSummary>('GET', '/api/stats');
    assertOk(status, data, 'cargar el resumen del índice');
    if (!data.exists || data.documentCount === 0) throw new IndexMissingError();
    return { documentCount: data.documentCount, sizeInBytes: data.sizeBytes, fileTypes: data.fileTypes };
}

// ---------------------------------------------------------------- indexing

export async function startIndexing(folder: string): Promise<IndexStatus> {
    const { status, data } = await apiRequest<IndexStatus>('POST', '/api/index', { folder });
    if (status === 409) throw new IndexingAlreadyRunningError();
    assertOk(status, data, 'actualizar el índice');
    return data;
}

export async function getIndexStatus(): Promise<IndexStatus> {
    const { status, data } = await apiRequest<IndexStatus>('GET', '/api/index/status');
    assertOk(status, data, 'leer el progreso de la indexación');
    return data;
}

export async function cancelIndexing(): Promise<IndexStatus> {
    const { status, data } = await apiRequest<IndexStatus>('POST', '/api/index/cancel');
    assertOk(status, data, 'cancelar la indexación');
    return data;
}
