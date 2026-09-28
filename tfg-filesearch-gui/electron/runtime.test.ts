import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { resolveBackendRuntime, RuntimeLayout } from './runtime'

const resources = path.join('/', 'opt', 'FileSearch', 'resources')
const appDir = path.join('/', 'src', 'gui', 'dist-electron')

function layout(overrides: Partial<RuntimeLayout>, files: string[] = []): RuntimeLayout {
    return {
        isPackaged: true,
        resourcesPath: resources,
        appDir,
        platform: 'win32',
        env: {},
        exists: (file) => files.includes(file),
        ...overrides,
    }
}

describe('resolveBackendRuntime', () => {
    it('uses the runtime bundled with the installer', () => {
        const files = [
            path.join(resources, 'jre', 'bin', 'java.exe'),
            path.join(resources, 'tesseract', 'tesseract.exe'),
            path.join(resources, 'tesseract', 'tessdata'),
        ]
        expect(resolveBackendRuntime(layout({}, files))).toEqual({
            jarPath: path.join(resources, 'filesearch.jar'),
            javaPath: path.join(resources, 'jre', 'bin', 'java.exe'),
            env: {
                FILESEARCH_TESSERACT_PATH: path.join(resources, 'tesseract'),
                TESSDATA_PREFIX: path.join(resources, 'tesseract', 'tessdata'),
            },
        })
    })

    it('falls back to the installed Java and Tesseract when nothing is bundled', () => {
        const runtime = resolveBackendRuntime(layout({ platform: 'linux' }))
        expect(runtime.javaPath).toBe('java')
        expect(runtime.env).toEqual({})
    })

    it('uses the freshly built engine in development', () => {
        const runtime = resolveBackendRuntime(layout({ isPackaged: false, platform: 'linux' }))
        expect(runtime.jarPath).toBe(path.join(appDir, '../../tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar'))
    })

    it('lets environment variables override everything', () => {
        const runtime = resolveBackendRuntime(layout({ env: { FILESEARCH_JAR: '/x/engine.jar', FILESEARCH_JAVA: '/x/java' } },
            [path.join(resources, 'jre', 'bin', 'java.exe')]))
        expect(runtime.jarPath).toBe('/x/engine.jar')
        expect(runtime.javaPath).toBe('/x/java')
    })
})
