import { app, BrowserWindow, shell, ipcMain, clipboard, dialog, IpcMainInvokeEvent } from 'electron'
import { existsSync } from 'node:fs'
import fs from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { isAllowedApiRequest, isOpenableDocumentPath } from './security'
import { normalizeIndexingStats, normalizeSearchStats } from './analytics'
import { Backend } from './backend'
import { BackendRuntime, resolveBackendRuntime } from './runtime'
import { supportReport } from './support'
import { canUpdateIndex, checkLicense, indexingLockedMessage, licenseState, LicenseDetails, LicenseState, MAX_LICENSE_CHARS } from './license'
import { LICENSE_PUBLIC_KEY } from './license-key'

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

const MAX_CLIPBOARD_CHARS = 100_000
const MAX_EXPORT_BYTES = 50 * 1024 * 1024

let win: BrowserWindow | null
let backend: Backend | null = null

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

function backendRuntime(): BackendRuntime {
    return resolveBackendRuntime({
        isPackaged: app.isPackaged,
        resourcesPath: process.resourcesPath,
        appDir: __dirname,
        platform: process.platform,
        env: process.env,
        exists: existsSync,
    })
}

/** Where the backend keeps its index, statistics and logs (same rule as the Java AppPaths). */
function dataHome(): string {
    return process.env.FILESEARCH_HOME || path.join(os.homedir(), '.filesearch')
}

/**
 * Folders the user picked in the folder dialog. Only these can be indexed, so a
 * compromised renderer cannot point the index somewhere else (which would also
 * drop the documents indexed so far).
 */
const approvedFoldersFile = () => path.join(app.getPath('userData'), 'approved-folders.json')

async function readApprovedFolders(): Promise<string[]> {
    try {
        const folders = JSON.parse(await fs.readFile(approvedFoldersFile(), 'utf-8'))
        return Array.isArray(folders) ? folders.filter((f): f is string => typeof f === 'string') : []
    } catch {
        return []
    }
}

async function approveFolder(folder: string): Promise<void> {
    const folders = new Set(await readApprovedFolders())
    folders.add(folder)
    await fs.writeFile(approvedFoldersFile(), JSON.stringify([...folders]), 'utf-8')
}

// --- Licence ---
// The installed licence and the date of the first launch live in the app's user data folder.
const licenseFile = () => path.join(app.getPath('userData'), 'license.lic')
const trialFile = () => path.join(app.getPath('userData'), 'trial.json')

async function trialStarted(): Promise<Date> {
    try {
        const { startedAt } = JSON.parse(await fs.readFile(trialFile(), 'utf-8'))
        const date = new Date(startedAt)
        if (typeof startedAt === 'string' && !Number.isNaN(date.getTime())) return date
    } catch {
        // First launch, or an unreadable file: start the trial now.
    }
    const now = new Date()
    await fs.writeFile(trialFile(), JSON.stringify({ startedAt: now.toISOString() }), 'utf-8')
    return now
}

async function installedLicense(): Promise<LicenseDetails | null> {
    try {
        const check = checkLicense(await fs.readFile(licenseFile(), 'utf-8'), LICENSE_PUBLIC_KEY)
        return check.ok ? check.details : null
    } catch {
        return null
    }
}

async function currentLicenseState(): Promise<LicenseState> {
    return licenseState(await installedLicense(), await trialStarted(), new Date())
}

function createWindow() {
    const distDir = process.env.DIST || '';

    win = new BrowserWindow({
        width: 1000,
        height: 700,
        minWidth: 800,
        minHeight: 600,
        title: 'File Search',
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

function registerIpcHandlers() {
    handle('open-path', async (_event, filePath: unknown) => {
        if (!isOpenableDocumentPath(filePath) || !(await isExistingFile(filePath))) {
            return 'No se puede abrir este archivo: ya no existe o no es un tipo de documento admitido.'
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
        if (canceled || filePaths.length === 0) return null
        await approveFolder(filePaths[0])
        return filePaths[0]
    })

    handle('get-analytics', async () => {
        const statsDir = dataHome()
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

    handle('license:status', () => currentLicenseState())

    handle('support:copy-info', async () => {
        const runtime = backendRuntime()
        backend ??= new Backend(runtime)
        const health = await backend.request('GET', '/api/health')
        const stats = health.status === 200 ? await backend.request('GET', '/api/stats') : null
        const text = supportReport({
            appVersion: app.getVersion(),
            platform: `${process.platform} ${os.release()}`,
            health: health.status === 200 ? (health.data as object) : { error: health.error ?? `HTTP ${health.status}` },
            stats: stats?.status === 200 ? (stats.data as object) : null,
            license: await currentLicenseState(),
            usesBundledJava: runtime.javaPath !== 'java',
            usesBundledTesseract: 'FILESEARCH_TESSERACT_PATH' in runtime.env,
        })
        clipboard.writeText(text)
        return text
    })

    handle('license:install', async () => {
        const { canceled, filePaths } = await dialog.showOpenDialog(win!, {
            properties: ['openFile'],
            filters: [{ name: 'Licencia', extensions: ['lic'] }],
        })
        if (canceled || filePaths.length === 0) return { installed: false, state: await currentLicenseState() }
        let text: string
        try {
            const file = await fs.stat(filePaths[0])
            if (file.size > MAX_LICENSE_CHARS) throw new Error()
            text = await fs.readFile(filePaths[0], 'utf-8')
        } catch {
            return { installed: false, error: 'Este archivo no es una licencia.', state: await currentLicenseState() }
        }
        const check = checkLicense(text, LICENSE_PUBLIC_KEY)
        if (!check.ok) return { installed: false, error: check.reason, state: await currentLicenseState() }
        await fs.writeFile(licenseFile(), text.trim() + '\n', 'utf-8')
        return { installed: true, state: await currentLicenseState() }
    })

    handle('api:request', async (_event, request: unknown) => {
        if (!isAllowedApiRequest(request)) {
            throw new Error('Request not allowed')
        }
        if (request.path === '/api/index') {
            const { folder } = request.body as { folder: string }
            if (!(await readApprovedFolders()).includes(folder)) {
                throw new Error('Vuelve a elegir la carpeta en Configuración antes de indexarla.')
            }
            const license = await currentLicenseState()
            if (!canUpdateIndex(license)) {
                throw new Error(indexingLockedMessage(license))
            }
        }
        backend ??= new Backend(backendRuntime())
        return backend.request(request.method, request.path, request.body)
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
    backend?.stop()
})

app.whenReady().then(() => {
    registerIpcHandlers()
    createWindow()
})
