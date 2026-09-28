import { AppConfig } from '../types';

export type EsMethod = 'GET' | 'HEAD' | 'POST';

export interface EsResponse<T = any> {
    status: number;
    data: T;
}

export class SearchEngineUnavailableError extends Error {
    constructor(reason: 'timeout' | 'unreachable' | 'no-transport') {
        super(reason === 'timeout'
            ? 'The search engine took too long to respond.'
            : reason === 'unreachable'
                ? 'The search engine is not running. Start Elasticsearch and try again.'
                : 'Search requires the desktop application.');
        this.name = 'SearchEngineUnavailableError';
    }
}

const DEV_PROXY_BASE = '/api';

/**
 * Sends a request to the local search engine.
 *
 * In the desktop app requests go through the main process, which only allows
 * read-only calls to a search engine on this computer. The browser fallback
 * exists for `vite` development only and goes through the dev-server proxy.
 */
export async function esRequest<T = any>(
    target: AppConfig['elasticsearch'],
    method: EsMethod,
    path: string,
    body?: unknown,
): Promise<EsResponse<T>> {
    if (window.electronAPI) {
        const response = await window.electronAPI.esRequest(target, { method, path, body });
        if (response.status === 0) {
            throw new SearchEngineUnavailableError(response.error ?? 'unreachable');
        }
        return { status: response.status, data: response.data };
    }

    if (!import.meta.env.DEV) {
        throw new SearchEngineUnavailableError('no-transport');
    }

    let response: Response;
    try {
        response = await fetch(DEV_PROXY_BASE + path, {
            method,
            headers: body ? { 'Content-Type': 'application/json' } : undefined,
            body: body ? JSON.stringify(body) : undefined,
        });
    } catch {
        throw new SearchEngineUnavailableError('unreachable');
    }
    const isJson = response.headers.get('content-type')?.includes('application/json');
    const data = method !== 'HEAD' && isJson ? await response.json() : null;
    return { status: response.status, data };
}
