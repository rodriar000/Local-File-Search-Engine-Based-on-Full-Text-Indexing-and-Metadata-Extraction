/**
 * Plain-text summary a user can paste into a support request. It says which
 * versions are installed and whether each part works, and never includes
 * document names, folder paths or contents.
 */
import type { LicenseState } from './license'

export interface SupportFacts {
    appVersion: string
    platform: string
    /** Answer of GET /api/health, or the reason the engine could not be reached. */
    health: { java?: string; os?: string; ocrAvailable?: boolean; ocrProblem?: string } | { error: string }
    /** Answer of GET /api/stats, when the engine is running. */
    stats: { documentCount?: number; sizeBytes?: number; fileTypes?: Record<string, number> } | null
    license: LicenseState
    usesBundledJava: boolean
    usesBundledTesseract: boolean
}

function licenseLine(license: LicenseState): string {
    switch (license.kind) {
        case 'trial':
            return `prueba, quedan ${license.daysLeft} días`
        case 'trial-ended':
            return 'prueba terminada'
        case 'licensed':
            return `licencia ${license.details.id}, ${license.details.expiresAt ? `hasta ${license.details.expiresAt}` : 'sin caducidad'}`
        case 'expired':
            return `licencia ${license.details.id} caducada el ${license.details.expiresAt}`
    }
}

export function supportReport(facts: SupportFacts): string {
    const lines = [
        'Información para soporte (sin nombres ni contenido de documentos)',
        `Aplicación: ${facts.appVersion} (${facts.platform})`,
        `Licencia: ${licenseLine(facts.license)}`,
    ]
    if ('error' in facts.health) {
        lines.push(`Buscador: no disponible (${facts.health.error})`)
    } else {
        lines.push(`Buscador: funcionando, Java ${facts.health.java ?? '?'} ${facts.usesBundledJava ? '(incluido)' : '(del sistema)'}`)
        lines.push(`Sistema: ${facts.health.os ?? '?'}`)
        lines.push(facts.health.ocrAvailable
            ? `OCR: disponible ${facts.usesBundledTesseract ? '(incluido)' : '(del sistema)'}`
            : `OCR: no disponible (${facts.health.ocrProblem ?? 'motivo desconocido'})`)
    }
    if (facts.stats) {
        const types = Object.entries(facts.stats.fileTypes ?? {})
            .sort((a, b) => b[1] - a[1])
            .map(([type, count]) => `${type} ${count}`)
            .join(', ')
        lines.push(`Documentos indexados: ${facts.stats.documentCount ?? 0}${types ? ` (${types})` : ''}`)
        lines.push(`Tamaño del índice: ${Math.round((facts.stats.sizeBytes ?? 0) / (1024 * 1024))} MB`)
    }
    return lines.join('\n')
}
