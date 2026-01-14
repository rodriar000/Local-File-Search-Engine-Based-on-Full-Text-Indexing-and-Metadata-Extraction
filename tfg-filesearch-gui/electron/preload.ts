import { ipcRenderer, contextBridge } from 'electron'

// --------- Expose some API to the Renderer process ---------
contextBridge.exposeInMainWorld('electronAPI', {
    openPath: (path: string) => ipcRenderer.invoke('open-path', path),
    showInFolder: (path: string) => ipcRenderer.invoke('show-in-folder', path),
    copyToClipboard: (text: string) => ipcRenderer.invoke('copy-to-clipboard', text),
    saveExport: (payload: { type: string, data: string, defaultPath?: string }) => ipcRenderer.invoke('export:save', payload),
    on: (channel: string, callback: (event: any, ...args: any[]) => void) => {
        ipcRenderer.on(channel, callback)
    },
    off: (channel: string, callback: (...args: any[]) => void) => {
        ipcRenderer.removeListener(channel, callback)
    }
})
