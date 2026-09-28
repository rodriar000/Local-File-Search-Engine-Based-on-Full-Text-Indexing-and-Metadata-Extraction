/**
 * Where the search engine and the programs it needs are.
 *
 * The installer ships them in the app's resources folder (see
 * scripts/bundle-runtime.sh): the engine JAR, a Java runtime and, on Windows,
 * Tesseract with Spanish data. In development they come from the build output
 * and the programs installed on the computer. Environment variables override both.
 */
import path from 'node:path'

export interface RuntimeLayout {
    isPackaged: boolean
    /** Electron's process.resourcesPath. */
    resourcesPath: string
    /** Folder of the compiled main process (dist-electron). */
    appDir: string
    platform: NodeJS.Platform
    env: Record<string, string | undefined>
    /** Whether a file or folder exists. */
    exists: (file: string) => boolean
}

export interface BackendRuntime {
    jarPath: string
    javaPath: string
    /** Extra environment for the engine: where the bundled Tesseract and its language data are. */
    env: Record<string, string>
}

export function resolveBackendRuntime(layout: RuntimeLayout): BackendRuntime {
    const { env, platform, exists } = layout
    const bundled = layout.isPackaged ? layout.resourcesPath : path.join(layout.appDir, '../build/runtime')

    // In development, the freshly built JAR rather than the copy made for the installer.
    const jarPath = env.FILESEARCH_JAR
        || (layout.isPackaged
            ? path.join(bundled, 'filesearch.jar')
            : path.join(layout.appDir, '../../tfg-filesearch/target/filesearch-1.0.0-jar-with-dependencies.jar'))

    const bundledJava = path.join(bundled, 'jre', 'bin', platform === 'win32' ? 'java.exe' : 'java')
    const javaPath = env.FILESEARCH_JAVA || (exists(bundledJava) ? bundledJava : 'java')

    const tesseract = path.join(bundled, 'tesseract')
    const tessdata = path.join(tesseract, 'tessdata')
    const engineEnv: Record<string, string> = {}
    if (exists(path.join(tesseract, platform === 'win32' ? 'tesseract.exe' : 'tesseract')) && exists(tessdata)) {
        engineEnv.FILESEARCH_TESSERACT_PATH = tesseract
        engineEnv.TESSDATA_PREFIX = tessdata
    }
    return { jarPath, javaPath, env: engineEnv }
}
