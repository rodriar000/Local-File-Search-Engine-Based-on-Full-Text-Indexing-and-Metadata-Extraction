"use strict";
const electron = require("electron");
electron.contextBridge.exposeInMainWorld("electronAPI", {
  openPath: (path) => electron.ipcRenderer.invoke("open-path", path),
  showInFolder: (path) => electron.ipcRenderer.invoke("show-in-folder", path),
  copyToClipboard: (text) => electron.ipcRenderer.invoke("copy-to-clipboard", text),
  saveExport: (payload) => electron.ipcRenderer.invoke("export:save", payload),
  on: (channel, callback) => {
    electron.ipcRenderer.on(channel, callback);
  },
  off: (channel, callback) => {
    electron.ipcRenderer.removeListener(channel, callback);
  }
});
