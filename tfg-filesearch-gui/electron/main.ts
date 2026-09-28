import { app, BrowserWindow, shell, ipcMain, clipboard, dialog, IpcMainInvokeEvent } from 'electron'
import { spawn, ChildProcess } from 'node:child_process'
import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import {
    EsRequest,
    EsTarget,
    isAllowedEsRequest,
    isOpenableDocumentPath,
    isValidEsTarget,
    isValidIndexFolder,
} from './security'
import { normalizeIndexingStats, normalizeSearchStats } from './analytics'

// --- GPU Management Strategy ---
// Fix for "Black Screen" / EGL Driver errors on macOS Intel
if (process.env.ELECTRON_DISABLE_GPU === '1') {
    console.log('[Main-Process] Hardware acceleration disabled via ELECTRON_DISABLE_GPU=1');
    app.disableHardwareAcceleration();
}

// Optional: Force software rendering if needed
if (process.env.ELECTRON_FORCE_SWIFTSHADER === '1') {
    console.log('[Main-Process] Forcing SwiftShader (Software Rendering)');
    app.commandLine.appendSwitch('use-gl', 'swiftshader');
}

process.env.DIST = path.join(__dirname, '../dist')
process.env.VITE_PUBLIC = app.isPackaged ? process.env.DIST : path.join(__dirname, '../public')

const ES_TIMEOUT_MS = 10_000
const MAX_CLIPBOARD_CHARS = 100_000
const MAX_EXPORT_BYTES = 50 * 1024 * 1024
const MAX_REINDEX_OUTPUT_CHARS = 64 * 1024

let win: BrowserWindow | null
let reindexProcess: ChildProcess | null = null
/** Set synchronously so two quick clicks cannot both start an indexer. */
let reindexInFlight = false

const VITE_DEV_SERVER_URL = process.env['VITE_DEV_SERVER_URL']

/** IPC is only accepted from the app's own page, never from a navigated or embedded frame. */
function isTrustedSender(event: IpcMainInvokeEvent): boolean {
    const url = event.senderFrame?.url ?? ''
    if (VITE_DEV_SERVER_URL && url.startsWith(VITE_DEV_SERVER_URL)) return true
    return url.startsWith('file://')
}

function handle<Args extends unknown[], Result>(
    channel: string,
    listener: (event: IpcMainInvokeEvent, ...args: Args) => Promise<Result> | Result,
) {
    ipcMain.handle(channel, (event, ...args) => {
        if (!isTrustedSender(event)) {
            throw new Error(`Rejected IPC call to "${channel}" from an untrusted frame`)
        }
        return listener(event, ...(args as Args))
    })
}

async function isExistingFile(filePath: string): Promise<boolean> {
    try {
        return (await fs.stat(filePath)).isFile()
    } catch {
        return false
    }
}

async function isExistingDirectory(dirPath: string): Promise<boolean> {
    try {
        return (await fs.stat(dirPath)).isDirectory()
    } catch {
        return false
    }
}

function resolveIndexerJar(): string {
    if (process.env.FILESEARCH_JAR) return process.env.FILESEARCH_JAR
    return app.isPackaged
        ? path.join(process.resourcesPath, 'filesearch.jar')
        : path.join(__dirname, '../../tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar')
}

function createWindow() {
    const publicDir = process.env.VITE_PUBLIC || '';
    const distDir = process.env.DIST || '';

    win = new BrowserWindow({
        width: 1000,
        height: 700,
        minWidth: 800,
        minHeight: 600,
        title: 'File Search',
        icon: path.join(publicDir, 'electron-vite.svg'),
        show: false, // Hide until ready to prevent black flash
        webPreferences: {
            preload: path.join(__dirname, 'preload.js'),
            nodeIntegration: false,
            contextIsolation: true,
            sandbox: true,
            webSecurity: true,
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
        backgroundColor: '#ffffff',
        frame: false // Frameless for custom UI
    })

    win.once('ready-to-show', () => {
        win?.show();
    });

    win.webContents.on('did-fail-load', (_event, errorCode, errorDescription) => {
        console.error(`[Main-Process] Failed to load renderer: ${errorCode} - ${errorDescription}`);
    });

    win.webContents.on('render-process-gone', (_event, details) => {
        console.error(`[Main-Process] Renderer process gone. Reason: ${details.reason}, Exit Code: ${details.exitCode}`);
    });

    // The renderer never opens windows or leaves the app page.
    win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
    win.webContents.on('will-navigate', (event, url) => {
        const isAppPage = VITE_DEV_SERVER_URL ? url.startsWith(VITE_DEV_SERVER_URL) : url.startsWith('file://');
        if (!isAppPage) event.preventDefault();
    });

    if (VITE_DEV_SERVER_URL) {
        win.loadURL(VITE_DEV_SERVER_URL);
    } else {
        win.loadFile(path.join(distDir, 'index.html'));
    }
}

async function forwardEsRequest(target: EsTarget, request: EsRequest) {
    const base = target.url.replace(/\/$/, '')
    try {
        const response = await fetch(base + request.path, {
            method: request.method,
            headers: request.body ? { 'Content-Type': 'application/json' } : undefined,
            body: request.body ? JSON.stringify(request.body) : undefined,
            signal: AbortSignal.timeout(ES_TIMEOUT_MS),
        })
        const isJson = response.headers.get('content-type')?.includes('application/json')
        const data = request.method !== 'HEAD' && isJson ? await response.json() : null
        return { status: response.status, data }
    } catch (error) {
        const reason = error instanceof Error && error.name === 'TimeoutError' ? 'timeout' : 'unreachable'
        return { status: 0, data: null, error: reason }
    }
}

function registerIpcHandlers() {
    handle('open-path', async (_event, filePath: unknown) => {
        if (!isOpenableDocumentPath(filePath) || !(await isExistingFile(filePath))) {
            return 'This file cannot be opened: it no longer exists or is not a supported document type.'
        }
        return shell.openPath(filePath)
    })

    handle('show-in-folder', async (_event, filePath: unknown) => {
        if (isOpenableDocumentPath(filePath) && (await isExistingFile(filePath))) {
            shell.showItemInFolder(filePath)
        }
    })

    handle('copy-to-clipboard', (_event, text: unknown) => {
        if (typeof text === 'string' && text.length <= MAX_CLIPBOARD_CHARS) {
            clipboard.writeText(text)
        }
    })

    handle('export:save', async (_event, payload: unknown) => {
        const { type, data, defaultPath } = (payload ?? {}) as Record<string, unknown>
        if ((type !== 'json' && type !== 'csv') || typeof data !== 'string' || data.length > MAX_EXPORT_BYTES) {
            throw new Error('Invalid export request')
        }
        const suggested = typeof defaultPath === 'string' ? path.basename(defaultPath) : `export.${type}`
        const { filePath } = await dialog.showSaveDialog(win!, {
            defaultPath: suggested,
            filters: [{ name: type.toUpperCase(), extensions: [type] }]
        });
        if (!filePath) return null
        await fs.writeFile(filePath, data, 'utf-8');
        return filePath;
    });

    handle('select-folder', async () => {
        const { canceled, filePaths } = await dialog.showOpenDialog(win!, {
            properties: ['openDirectory'],
        })
        return canceled || filePaths.length === 0 ? null : filePaths[0]
    })

    handle('reindex', async (_event, folder: unknown) => {
        if (reindexInFlight) {
            throw new Error('Indexing is already running.')
        }
        reindexInFlight = true
        try {
            return await runIndexer(folder)
        } finally {
            reindexInFlight = false
        }
    })

    async function runIndexer(folder: unknown): Promise<string> {
        if (!isValidIndexFolder(folder) || !(await isExistingDirectory(folder))) {
            throw new Error('Choose an existing folder to index.')
        }
        const jarPath = resolveIndexerJar()
        if (!(await isExistingFile(jarPath))) {
            throw new Error(`Indexer not found at ${jarPath}. Build the backend with "mvn package" first.`)
        }

        return new Promise<string>((resolve, reject) => {
            const child = spawn('java', ['-jar', jarPath, 'update-index', '--', folder], { windowsHide: true })
            reindexProcess = child

            let output = ''
            const append = (chunk: Buffer) => {
                output = (output + chunk.toString()).slice(-MAX_REINDEX_OUTPUT_CHARS)
            }
            child.stdout?.on('data', append)
            child.stderr?.on('data', append)

            child.on('error', (error: NodeJS.ErrnoException) => {
                reindexProcess = null
                reject(new Error(error.code === 'ENOENT'
                    ? 'Java was not found. Install Java 17 or later to run the indexer.'
                    : `Could not start the indexer: ${error.message}`))
            })

            child.on('close', (code) => {
                reindexProcess = null
                if (code === 0) {
                    resolve(output)
                } else {
                    reject(new Error(`Indexing failed (exit code ${code}).\n${output}`))
                }
            })
        })
    }

    handle('get-analytics', async () => {
        const statsDir = path.join(os.homedir(), '.filesearch')
        const readJson = async (file: string) => {
            try {
                return JSON.parse(await fs.readFile(path.join(statsDir, file), 'utf-8'))
            } catch {
                return null
            }
        }
        return {
            search: normalizeSearchStats(await readJson('search_stats.json')),
            indexing: normalizeIndexingStats(await readJson('indexing_stats.json')),
        }
    })

    handle('es:request', async (_event, target: unknown, request: unknown) => {
        if (!isValidEsTarget(target)) {
            throw new Error('The search engine address must be on this computer (localhost).')
        }
        if (!isAllowedEsRequest(request, target.indexName)) {
            throw new Error('Search engine request not allowed')
        }
        return forwardEsRequest(target, request)
    })
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

app.on('before-quit', () => {
    reindexProcess?.kill()
})

app.whenReady().then(() => {
    registerIpcHandlers()
    createWindow()
})
