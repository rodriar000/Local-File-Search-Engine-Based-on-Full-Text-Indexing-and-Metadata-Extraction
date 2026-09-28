/**
 * Lifecycle of the local search backend (the Java `serve` command).
 *
 * The main process starts it on demand with a fresh random token, reads the
 * port from its ready line, and is the only thing that ever talks to it. The
 * backend stops by itself when its stdin closes, so it cannot outlive the app.
 */
import { spawn, ChildProcess } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import fs from 'node:fs/promises'

const READY_PATTERN = /^FILESEARCH_READY port=(\d+)\s*$/m
const START_TIMEOUT_MS = 60_000
const REQUEST_TIMEOUT_MS = 30_000
const MAX_LOG_CHARS = 16 * 1024
/** Exit code the backend uses when another process already has the index open. */
const EXIT_INDEX_LOCKED = 3

export interface BackendResponse {
    /** HTTP status, or 0 when the backend could not be reached. */
    status: number
    data: unknown
    error?: string
}

export interface BackendOptions {
    jarPath: string
    javaPath?: string
}

export class Backend {
    private process: ChildProcess | null = null
    private starting: Promise<number> | null = null
    private port = 0
    private token = ''
    private stopping = false

    constructor(private readonly options: BackendOptions) {}

    /** Sends a request, starting the backend first if it is not running. */
    async request(method: 'GET' | 'POST', path: string, body?: unknown): Promise<BackendResponse> {
        let port: number
        try {
            port = await this.ensureStarted()
        } catch (error) {
            return { status: 0, data: null, error: error instanceof Error ? error.message : String(error) }
        }
        try {
            const response = await fetch(`http://127.0.0.1:${port}${path}`, {
                method,
                headers: {
                    Authorization: `Bearer ${this.token}`,
                    ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
                },
                body: body !== undefined ? JSON.stringify(body) : undefined,
                signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
            })
            const isJson = response.headers.get('content-type')?.includes('application/json')
            return { status: response.status, data: isJson ? await response.json() : null }
        } catch (error) {
            const timedOut = error instanceof Error && error.name === 'TimeoutError'
            return {
                status: 0,
                data: null,
                error: timedOut ? 'The search engine took too long to respond.' : 'The search engine stopped unexpectedly.',
            }
        }
    }

    stop(): void {
        this.stopping = true
        const child = this.process
        if (!child) return
        // Closing stdin asks the backend to close the index cleanly; kill it if it does not.
        child.stdin?.end()
        const timer = setTimeout(() => child.kill(), 10_000)
        child.once('exit', () => clearTimeout(timer))
    }

    private ensureStarted(): Promise<number> {
        if (this.process && this.port) return Promise.resolve(this.port)
        if (!this.starting) {
            this.starting = this.start().finally(() => {
                this.starting = null
            })
        }
        return this.starting
    }

    private async start(): Promise<number> {
        if (this.stopping) throw new Error('The application is closing.')
        try {
            if (!(await fs.stat(this.options.jarPath)).isFile()) throw new Error()
        } catch {
            throw new Error(`Search engine not found at ${this.options.jarPath}. Build the backend with "mvn package" first.`)
        }

        this.token = randomBytes(32).toString('hex')
        const child = spawn(this.options.javaPath ?? 'java', ['-jar', this.options.jarPath, 'serve'], {
            env: { ...process.env, FILESEARCH_API_TOKEN: this.token },
            stdio: ['pipe', 'pipe', 'pipe'],
            windowsHide: true,
        })
        this.process = child

        let log = ''
        child.stderr?.on('data', (chunk: Buffer) => {
            log = (log + chunk.toString()).slice(-MAX_LOG_CHARS)
        })

        return new Promise<number>((resolve, reject) => {
            let stdout = ''
            let settled = false
            const settle = (error: Error | null, port = 0) => {
                if (settled) return
                settled = true
                clearTimeout(timer)
                if (error) {
                    reject(error)
                } else {
                    this.port = port
                    resolve(port)
                }
            }
            const timer = setTimeout(() => {
                child.kill()
                settle(new Error('The search engine did not start in time.'))
            }, START_TIMEOUT_MS)

            child.stdout?.on('data', (chunk: Buffer) => {
                if (settled) return
                stdout = (stdout + chunk.toString()).slice(-MAX_LOG_CHARS)
                const match = READY_PATTERN.exec(stdout)
                if (match) settle(null, Number(match[1]))
            })

            child.on('error', (error: NodeJS.ErrnoException) => {
                this.process = null
                this.port = 0
                settle(new Error(error.code === 'ENOENT'
                    ? 'Java was not found. Install Java 17 or later to run the search engine.'
                    : `Could not start the search engine: ${error.message}`))
            })

            child.on('exit', (code) => {
                this.process = null
                this.port = 0
                if (code === EXIT_INDEX_LOCKED) {
                    settle(new Error('The index is in use by another copy of the application. Close it and try again.'))
                } else {
                    if (!this.stopping) console.error(`[backend] exited with code ${code}\n${log}`)
                    settle(new Error(`The search engine stopped (exit code ${code}).`))
                }
            })
        })
    }
}
