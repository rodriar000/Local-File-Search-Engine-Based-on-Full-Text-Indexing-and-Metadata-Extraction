/**
 * Data protection reports: the documents that mention a person, saved as CSV
 * (for Excel) or PDF (to keep with the request). The report comes from the
 * renderer, which displays untrusted document names, so it is validated here
 * and every value is escaped. These functions are pure so they can be unit tested.
 */
import { dataTypeLabel, PersonalDataReport, ReportDocument } from '../src/shared/personalData'

export const MAX_REPORT_DOCUMENTS = 10_000

export function isValidReport(value: unknown): value is PersonalDataReport {
    if (typeof value !== 'object' || value === null) return false
    const r = value as Record<string, unknown>
    const optionalText = (v: unknown, max: number) => v === undefined || v === null || (typeof v === 'string' && v.length <= max)
    return optionalText(r.name, 200) && optionalText(r.identifier, 100) && optionalText(r.identifierType, 30)
        && typeof r.generatedAt === 'string' && !Number.isNaN(Date.parse(r.generatedAt))
        && typeof r.total === 'number' && typeof r.truncated === 'boolean'
        && Array.isArray(r.documents) && r.documents.length <= MAX_REPORT_DOCUMENTS
        && r.documents.every(isValidDocument)
}

function isValidDocument(value: unknown): value is ReportDocument {
    if (typeof value !== 'object' || value === null) return false
    const d = value as Record<string, unknown>
    return typeof d.path === 'string' && d.path.length <= 4096
        && typeof d.filename === 'string' && d.filename.length <= 1024
        && typeof d.byName === 'boolean' && typeof d.byIdentifier === 'boolean'
        && (d.modifiedAt === undefined || d.modifiedAt === null || typeof d.modifiedAt === 'string')
        && Array.isArray(d.dataTypes) && d.dataTypes.length <= 20 && d.dataTypes.every((t) => typeof t === 'string' && t.length <= 30)
}

/** Who the report is about, e.g. "Juan Pérez García (DNI 12345678Z)". */
export function reportSubject(report: PersonalDataReport): string {
    const id = report.identifier ? `${dataTypeLabel(report.identifierType ?? '')} ${report.identifier}` : ''
    if (report.name && id) return `${report.name} (${id})`
    return report.name || id
}

function date(value: string | undefined | null, withTime = false): string {
    if (!value) return ''
    const parsed = new Date(value)
    if (Number.isNaN(parsed.getTime())) return ''
    return withTime ? parsed.toLocaleString('es-ES') : parsed.toLocaleDateString('es-ES')
}

function foundBy(doc: ReportDocument): string {
    return [doc.byName ? 'Nombre' : '', doc.byIdentifier ? 'Identificador' : ''].filter(Boolean).join(' y ')
}

function dataTypes(doc: ReportDocument): string {
    return doc.dataTypes.map(dataTypeLabel).join(', ')
}

/**
 * A cell for Excel in Spanish (semicolon separated). Values starting with = + - @
 * are prefixed so a crafted file name cannot run as a formula.
 */
function csvCell(value: string): string {
    const safe = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value
    return /[";\n\r]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe
}

export function reportCsv(report: PersonalDataReport): string {
    const rows = [
        ['Informe de documentos con datos personales'],
        ['Persona', reportSubject(report)],
        ['Fecha del informe', date(report.generatedAt, true)],
        ['Documentos', String(report.total)],
        [],
        ['Documento', 'Ruta', 'Modificado', 'Encontrado por', 'Datos que contiene'],
        ...report.documents.map((doc) => [doc.filename, doc.path, date(doc.modifiedAt), foundBy(doc), dataTypes(doc)]),
    ]
    // BOM so Excel reads the accents correctly.
    return '﻿' + rows.map((row) => row.map(csvCell).join(';')).join('\r\n') + '\r\n'
}

export function escapeHtml(value: string): string {
    return value.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!))
}

/** Printable page for the PDF. It has no scripts and loads nothing. */
export function reportHtml(report: PersonalDataReport): string {
    const rows = report.documents.map((doc) => `
        <tr>
            <td><strong>${escapeHtml(doc.filename)}</strong><br><span class="path">${escapeHtml(doc.path)}</span></td>
            <td>${escapeHtml(date(doc.modifiedAt))}</td>
            <td>${escapeHtml(foundBy(doc))}</td>
            <td>${escapeHtml(dataTypes(doc))}</td>
        </tr>`).join('')
    const truncated = report.truncated
        ? `<p class="warning">Hay ${report.total} documentos; este informe lista los ${report.documents.length} primeros por ruta. Afina la búsqueda para verlos todos.</p>`
        : ''
    const criteria = [
        report.name ? `contienen el nombre completo «${escapeHtml(report.name)}» (sin distinguir mayúsculas ni tildes)` : '',
        report.identifier ? `contienen el ${escapeHtml(dataTypeLabel(report.identifierType ?? ''))} ${escapeHtml(report.identifier)} escrito de cualquier forma` : '',
    ].filter(Boolean).join(', o ')
    return `<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">
<title>Informe de datos personales</title>
<style>
    body { font-family: Arial, Helvetica, sans-serif; font-size: 10pt; color: #111; margin: 0; }
    h1 { font-size: 16pt; margin: 0 0 4pt; }
    .meta { color: #444; margin: 0 0 12pt; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; vertical-align: top; border-bottom: 1px solid #ccc; padding: 4pt; }
    th { background: #f0f0f0; }
    tr { page-break-inside: avoid; }
    .path { color: #555; font-size: 8pt; word-break: break-all; }
    .warning { color: #8a4b00; }
    .note { color: #555; font-size: 8pt; margin-top: 12pt; }
</style></head>
<body>
<h1>Informe de documentos con datos personales</h1>
<p class="meta">Persona: <strong>${escapeHtml(reportSubject(report))}</strong><br>
Fecha: ${escapeHtml(date(report.generatedAt, true))}<br>
Documentos encontrados: <strong>${report.total}</strong></p>
${truncated}
<table>
<thead><tr><th>Documento</th><th>Modificado</th><th>Encontrado por</th><th>Datos que contiene</th></tr></thead>
<tbody>${rows || '<tr><td colspan="4">No hay documentos indexados que mencionen a esta persona.</td></tr>'}</tbody>
</table>
<p class="note">Se listan los documentos indexados en este ordenador que ${criteria}.
Los documentos sin texto legible (por ejemplo, escaneos que no se han podido leer) no pueden aparecer.
Informe generado localmente; ningún dato ha salido del equipo.</p>
</body></html>`
}

export function reportFileName(report: PersonalDataReport, extension: 'csv' | 'pdf'): string {
    const who = (report.identifier || report.name || 'persona').normalize('NFD').replace(/[̀-ͯ]/g, '')
        .replace(/[^A-Za-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 60)
    return `informe-rgpd-${who}-${report.generatedAt.slice(0, 10)}.${extension}`
}
