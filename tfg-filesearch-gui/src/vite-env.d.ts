/// <reference types="vite/client" />

interface ElectronAPI {
    openPath: (path: string) => Promise<string>;
    showInFolder: (path: string) => Promise<void>;
    copyToClipboard: (text: string) => Promise<void>;
    saveExport: (payload: { type: string, data: string, defaultPath?: string }) => Promise<string | null>;
    on: (channel: string, callback: (event: any, ...args: any[]) => void) => void;
    off: (channel: string, callback: (...args: any[]) => void) => void;
}

interface Window {
    electronAPI: ElectronAPI;
    ipcRenderer: {
        invoke: (channel: string, ...args: any[]) => Promise<any>;
        on: (channel: string, callback: (event: any, ...args: any[]) => void) => void;
        removeAllListeners: (channel: string) => void;
    };
}
