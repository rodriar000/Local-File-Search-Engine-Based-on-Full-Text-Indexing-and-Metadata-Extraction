import { app, BrowserWindow, shell, ipcMain, clipboard } from 'electron'
import path from 'node:path'

// --- GPU Management Strategy ---
// Fix for "Black Screen" / EGL Driver errors on macOS Intel
if (process.env.ELECTRON_DISABLE_GPU === '1') {
    console.log('[Main-Process] ⚠️ Hardware acceleration disabled via ELECTRON_DISABLE_GPU=1');
    app.disableHardwareAcceleration();
}

// Optional: Force software rendering if needed
if (process.env.ELECTRON_FORCE_SWIFTSHADER === '1') {
    console.log('[Main-Process] ⚠️ Forcing SwiftShader (Software Rendering)');
    app.commandLine.appendSwitch('use-gl', 'swiftshader');
}

// Log generic GPU info
app.commandLine.appendSwitch('enable-logging');
app.commandLine.appendSwitch('v', '1');

process.env.DIST = path.join(__dirname, '../dist')
process.env.VITE_PUBLIC = app.isPackaged ? process.env.DIST : path.join(__dirname, '../public')

let win: BrowserWindow | null

const VITE_DEV_SERVER_URL = process.env['VITE_DEV_SERVER_URL']

function createWindow() {
    const publicDir = process.env.VITE_PUBLIC || '';
    const distDir = process.env.DIST || '';

    console.log('[Main-Process] Creating BrowserWindow...');

    win = new BrowserWindow({
        width: 1000,
        height: 700,
        minWidth: 800,
        minHeight: 600,
        title: 'File Search',
        icon: path.join(publicDir, 'electron-vite.svg'),
        show: false, // Critical: Hide until ready to prevent black flash
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            nodeIntegration: false,
            contextIsolation: true,
            webSecurity: false // Disable CORS for local Elasticsearch connection
        },
        titleBarStyle: 'hidden',
        titleBarOverlay: {
            color: '#00000000', // Transparent
            symbolColor: '#4b5563',
            height: 30
        },
        // Add vibrancy for macOS glass effect
        vibrancy: 'under-window',
        visualEffectState: 'active',
        backgroundColor: '#ffffff', // Set exact background to avoid black default, usage dependent on theme
        frame: false // Frameless for custom UI
    })

    // --- Lifecycle Logging & Debugging ---

    win.once('ready-to-show', () => {
        console.log('[Main-Process] Window ready to show');
        win?.show();
    });

    win.webContents.on('did-start-loading', () => {
        console.log('[Main-Process] WebContents started loading...');
    });

    win.webContents.on('did-finish-load', () => {
        console.log('[Main-Process] WebContents finished loading successfully');
        win?.webContents.send('main-process-message', (new Date).toLocaleString());
    });

    win.webContents.on('did-fail-load', (_event, errorCode, errorDescription, validatedURL) => {
        console.error(`[Main-Process] ❌ Failed to load: ${validatedURL}`);
        console.error(`[Main-Process] Error: ${errorCode} - ${errorDescription}`);
    });

    win.webContents.on('render-process-gone', (_event, details) => {
        console.error(`[Main-Process] ❌ Renderer process gone. Reason: ${details.reason}, Exit Code: ${details.exitCode}`);
    });

    win.webContents.on('unresponsive', () => {
        console.warn('[Main-Process] ⚠️ Renderer process unresponsive');
    });

    if (VITE_DEV_SERVER_URL) {
        console.log(`[Main-Process] Loading Dev Server: ${VITE_DEV_SERVER_URL}`);
        win.loadURL(VITE_DEV_SERVER_URL);
        // Open DevTools automatically if we are in dev mode and having issues
        // win.webContents.openDevTools(); 
    } else {
        console.log(`[Main-Process] Loading Production File: ${path.join(distDir, 'index.html')}`);
        win.loadFile(path.join(distDir, 'index.html'));
    }
}

// Quit when all windows are closed, except on macOS. There, it's common
// for applications and their menu bar to stay active until the user quits
// explicitly with Cmd + Q.
app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') {
        app.quit()
    }
})

app.on('activate', () => {
    // On OS X it's common to re-create a window in the app when the
    // dock icon is clicked and there are no other windows open.
    if (BrowserWindow.getAllWindows().length === 0) {
        createWindow()
    }
})

app.whenReady().then(() => {
    console.log('[Main-Process] App Ready');
    createWindow()

    // IPC Handlers
    ipcMain.handle('open-path', async (_, filePath: string) => {
        const error = await shell.openPath(filePath)
        return error
    })

    ipcMain.handle('show-in-folder', async (_, filePath: string) => {
        shell.showItemInFolder(filePath)
    })

    ipcMain.handle('copy-to-clipboard', async (_, text: string) => {
        clipboard.writeText(text)
    })

    // Export Handler
    ipcMain.handle('export:save', async (_, { type, data, defaultPath }: { type: 'json' | 'csv', data: string, defaultPath: string }) => {
        const { dialog } = require('electron');
        const fs = require('fs/promises');

        const { filePath } = await dialog.showSaveDialog(win, {
            defaultPath: defaultPath || `export.${type}`,
            filters: [
                { name: type.toUpperCase(), extensions: [type] }
            ]
        });

        if (filePath) {
            await fs.writeFile(filePath, data, 'utf-8');
            return filePath;
        }
        return null;
    });

    // Reindex Handler
    ipcMain.handle('reindex', async (_, pathToIndex: string) => {
        const { spawn } = require('child_process');
        const jarPath = path.join(__dirname, '../../tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar');

        return new Promise((resolve, reject) => {
            const child = spawn('java', ['-jar', jarPath, 'update-index', pathToIndex]);

            let output = '';
            let error = '';

            child.stdout.on('data', (data: any) => {
                output += data.toString();
            });

            child.stderr.on('data', (data: any) => {
                error += data.toString();
            });

            child.on('close', (code: number) => {
                if (code === 0) {
                    resolve(output);
                } else {
                    reject(new Error(`Reindex failed with code ${code}: ${error}`));
                }
            });
        });
    });

    ipcMain.handle('get-analytics', async () => {
        const homeDir = require('os').homedir();
        const fs = require('fs/promises');
        const path = require('path');

        const statsDir = path.join(homeDir, '.filesearch');

        const result = {
            search: null,
            indexing: null
        };

        try {
            const searchData = await fs.readFile(path.join(statsDir, 'search_stats.json'), 'utf-8');
            result.search = JSON.parse(searchData);
        } catch (e) { /* ignore */ }

        try {
            const indexData = await fs.readFile(path.join(statsDir, 'indexing_stats.json'), 'utf-8');
            result.indexing = JSON.parse(indexData);
        } catch (e) { /* ignore */ }

        return result;
    });
})
