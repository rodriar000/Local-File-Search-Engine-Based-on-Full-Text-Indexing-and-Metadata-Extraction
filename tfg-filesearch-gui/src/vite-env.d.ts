/// <reference types="vite/client" />

interface ApiBridgeResponse {
    /** HTTP status, or 0 when the search engine could not be started or reached. */
    status: number;
    data: any;
    /** Why the search engine is unavailable, when status is 0. */
    error?: string;
}

interface ElectronAPI {
    openPath: (path: string) => Promise<string>;
    showInFolder: (path: string) => Promise<void>;
    copyToClipboard: (text: string) => Promise<void>;
    saveExport: (payload: { type: string, data: string, defaultPath?: string }) => Promise<string | null>;
    selectFolder: () => Promise<string | null>;
    getAnalytics: () => Promise<import('./types').SystemAnalytics>;
    apiRequest: (request: { method: 'GET' | 'POST', path: string, body?: unknown }) => Promise<ApiBridgeResponse>;
}

interface Window {
    electronAPI?: ElectronAPI;
}
