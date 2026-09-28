/// <reference types="vite/client" />

interface EsBridgeResponse {
    /** HTTP status, or 0 when the search engine could not be reached. */
    status: number;
    data: any;
    error?: 'timeout' | 'unreachable';
}

interface ElectronAPI {
    openPath: (path: string) => Promise<string>;
    showInFolder: (path: string) => Promise<void>;
    copyToClipboard: (text: string) => Promise<void>;
    saveExport: (payload: { type: string, data: string, defaultPath?: string }) => Promise<string | null>;
    selectFolder: () => Promise<string | null>;
    reindex: (folder: string) => Promise<string>;
    getAnalytics: () => Promise<import('./types').SystemAnalytics>;
    esRequest: (
        target: { url: string, indexName: string },
        request: { method: 'GET' | 'HEAD' | 'POST', path: string, body?: unknown },
    ) => Promise<EsBridgeResponse>;
}

interface Window {
    electronAPI?: ElectronAPI;
}
