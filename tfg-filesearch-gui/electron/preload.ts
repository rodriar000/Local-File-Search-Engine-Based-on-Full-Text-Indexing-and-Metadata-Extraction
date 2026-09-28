import { ipcRenderer, contextBridge } from 'electron'

// --------- Expose a narrow, typed API to the Renderer process ---------
// Every call is validated again in the main process (see electron/security.ts).
contextBridge.exposeInMainWorld('electronAPI', {
    openPath: (path: string) => ipcRenderer.invoke('open-path', path),
    showInFolder: (path: string) => ipcRenderer.invoke('show-in-folder', path),
    copyToClipboard: (text: string) => ipcRenderer.invoke('copy-to-clipboard', text),
    saveExport: (payload: { type: string, data: string, defaultPath?: string }) => ipcRenderer.invoke('export:save', payload),
    selectFolder: () => ipcRenderer.invoke('select-folder'),
    getAnalytics: () => ipcRenderer.invoke('get-analytics'),
    apiRequest: (request: { method: string, path: string, body?: unknown }) => ipcRenderer.invoke('api:request', request),
})
