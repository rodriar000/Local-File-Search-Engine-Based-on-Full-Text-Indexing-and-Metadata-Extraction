import { describe, expect, it } from 'vitest'
import { escapeHtml, isValidReport, reportCsv, reportFileName, reportHtml, reportSubject } from './report'
import { PersonalDataReport } from '../src/shared/personalData'

const report: PersonalDataReport = {
    name: 'Juan Pérez García',
    identifier: '12345678Z',
    identifierType: 'dni',
    generatedAt: '2026-09-29T08:00:00Z',
    total: 2,
    truncated: false,
    documents: [
        { path: '/exp/perez/demanda.pdf', filename: 'demanda.pdf', extension: 'pdf', modifiedAt: '2025-03-01T10:00:00Z', byName: true, byIdentifier: true, dataTypes: ['dni', 'iban'] },
        { path: '/exp/perez/=cmd.txt', filename: '=cmd|<script>alert(1)</script>.txt', extension: 'txt', byName: true, byIdentifier: false, dataTypes: [] },
    ],
}

describe('report', () => {
    it('names the person and identifier', () => {
        expect(reportSubject(report)).toBe('Juan Pérez García (DNI 12345678Z)')
        expect(reportSubject({ ...report, name: undefined })).toBe('DNI 12345678Z')
        expect(reportSubject({ ...report, identifier: undefined })).toBe('Juan Pérez García')
    })

    it('writes a CSV that Excel in Spanish opens and that cannot run formulas', () => {
        const csv = reportCsv(report)
        expect(csv.startsWith('﻿Informe de documentos con datos personales')).toBe(true)
        expect(csv).toContain('demanda.pdf;/exp/perez/demanda.pdf;')
        expect(csv).toContain('Nombre y Identificador;DNI, IBAN')
        expect(csv).toContain("'=cmd|<script>alert(1)</script>.txt;/exp/perez/=cmd.txt")
    })

    it('escapes document names in the printable page', () => {
        const html = reportHtml(report)
        expect(html).not.toContain('<script>')
        expect(html).toContain('=cmd|&lt;script&gt;alert(1)&lt;/script&gt;.txt')
        expect(html).toContain("default-src 'none'")
        expect(html).toContain('Documentos encontrados: <strong>2</strong>')
        expect(escapeHtml(`<a href="x">'&`)).toBe('&lt;a href=&quot;x&quot;&gt;&#39;&amp;')
    })

    it('says when the list is incomplete', () => {
        expect(reportHtml({ ...report, total: 12000, truncated: true })).toContain('Hay 12000 documentos')
    })

    it('suggests a file name without accents or path separators', () => {
        expect(reportFileName(report, 'pdf')).toBe('informe-rgpd-12345678Z-2026-09-29.pdf')
        expect(reportFileName({ ...report, identifier: undefined, name: 'José/Ñúñez ../x' }, 'csv'))
            .toBe('informe-rgpd-Jose-Nunez-x-2026-09-29.csv')
    })

    it('refuses anything that is not a report', () => {
        expect(isValidReport(report)).toBe(true)
        expect(isValidReport(null)).toBe(false)
        expect(isValidReport({ ...report, documents: 'x' })).toBe(false)
        expect(isValidReport({ ...report, generatedAt: 'ayer' })).toBe(false)
        expect(isValidReport({ ...report, documents: [{ path: 1 }] })).toBe(false)
    })
})
