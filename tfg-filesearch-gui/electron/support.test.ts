import { describe, expect, it } from 'vitest'
import { supportReport } from './support'

describe('supportReport', () => {
    it('summarises the installation without document names', () => {
        const report = supportReport({
            appVersion: '1.0.0',
            platform: 'win32 10.0.22631',
            health: { java: '21.0.5', os: 'Windows 11 10.0', ocrAvailable: true },
            stats: { documentCount: 1234, sizeBytes: 50 * 1024 * 1024, fileTypes: { pdf: 1000, docx: 234 } },
            license: { kind: 'trial', daysLeft: 12, endsAt: '2026-10-10T00:00:00Z' },
            usesBundledJava: true,
            usesBundledTesseract: true,
        })
        expect(report).toContain('Licencia: prueba, quedan 12 días')
        expect(report).toContain('Java 21.0.5 (incluido)')
        expect(report).toContain('OCR: disponible (incluido)')
        expect(report).toContain('Documentos indexados: 1234 (pdf 1000, docx 234)')
        expect(report).toContain('Tamaño del índice: 50 MB')
    })

    it('says why the engine or OCR is not working', () => {
        const down = supportReport({
            appVersion: '1.0.0', platform: 'win32', health: { error: 'No se encuentra Java' }, stats: null,
            license: { kind: 'trial-ended', endedAt: '' }, usesBundledJava: false, usesBundledTesseract: false,
        })
        expect(down).toContain('Buscador: no disponible (No se encuentra Java)')
        const noOcr = supportReport({
            appVersion: '1.0.0', platform: 'linux', health: { java: '17', ocrAvailable: false, ocrProblem: 'tesseract was not found' },
            stats: null, license: { kind: 'trial', daysLeft: 1, endsAt: '' }, usesBundledJava: false, usesBundledTesseract: false,
        })
        expect(noOcr).toContain('OCR: no disponible (tesseract was not found)')
    })
})
