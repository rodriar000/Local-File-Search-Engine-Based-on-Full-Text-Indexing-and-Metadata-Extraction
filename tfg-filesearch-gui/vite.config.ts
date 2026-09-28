/// <reference types="vitest/config" />
import { defineConfig, Plugin } from 'vite'
import path from 'node:path'
import electron from 'vite-plugin-electron/simple'
import react from '@vitejs/plugin-react'

/**
 * Content-Security-Policy for the packaged app. The renderer loads only its own
 * bundle and talks to the search engine through IPC, so it needs no network access.
 * Applied at build time only: the dev server relies on inline scripts for HMR.
 */
const CONTENT_SECURITY_POLICY = [
    "default-src 'none'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data:",
    "font-src 'self' data:",
    "connect-src 'none'",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'none'",
].join('; ')

function contentSecurityPolicy(): Plugin {
    return {
        name: 'inject-content-security-policy',
        apply: 'build',
        transformIndexHtml: () => [{
            tag: 'meta',
            attrs: { 'http-equiv': 'Content-Security-Policy', content: CONTENT_SECURITY_POLICY },
            injectTo: 'head-prepend',
        }],
    }
}

// https://vitejs.dev/config/
export default defineConfig(() => {
    return {
        plugins: [
            react(),
            contentSecurityPolicy(),
            // Unit tests run in plain Node; the Electron plugin would shim Node built-ins.
            !process.env.VITEST && electron({
                main: {
                    // Shortcut of `build.lib.entry`.
                    entry: 'electron/main.ts',
                },
                preload: {
                    // Shortcut of `build.rollupOptions.input`.
                    input: path.join(__dirname, 'electron/preload.ts'),
                },
                renderer: {},
            }),
        ],
        test: {
            environment: 'node',
            include: ['src/**/*.test.ts', 'electron/**/*.test.ts'],
        },
    }
})
