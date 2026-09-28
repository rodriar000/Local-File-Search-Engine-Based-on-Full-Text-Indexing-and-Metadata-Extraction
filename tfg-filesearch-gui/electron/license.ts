/**
 * Offline licences.
 *
 * A licence is a small text file: "FSL1." + base64url(JSON details) + "." +
 * base64url(Ed25519 signature of everything before the last dot). It is issued
 * with scripts/license.mjs and checked here against the public key built into
 * the app, so no network access is ever needed.
 *
 * Without a valid licence the app runs as a 30-day trial. When the trial or the
 * licence ends, search, preview and opening documents keep working; only
 * updating the index stops, so a firm never loses access to its documents.
 *
 * These functions are pure so they can be unit tested.
 */
import { createPublicKey, verify } from 'node:crypto'

export const LICENSE_PREFIX = 'FSL1.'
export const MAX_LICENSE_CHARS = 4096
export const TRIAL_DAYS = 30
const DAY_MS = 24 * 60 * 60 * 1000
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

export interface LicenseDetails {
    /** Licence number, for support. */
    id: string
    /** Firm the licence was issued to. */
    customer: string
    /** Computers the firm paid for. Shown to the user; not enforced offline. */
    seats: number
    /** Issue date, YYYY-MM-DD. */
    issuedAt: string
    /** Last day the licence is valid, YYYY-MM-DD; null for a perpetual licence. */
    expiresAt: string | null
}

export type LicenseCheck = { ok: true; details: LicenseDetails } | { ok: false; reason: string }

export type LicenseState =
    | { kind: 'trial'; daysLeft: number; endsAt: string }
    | { kind: 'trial-ended'; endedAt: string }
    | { kind: 'licensed'; details: LicenseDetails; daysLeft: number | null }
    | { kind: 'expired'; details: LicenseDetails }

const base64url = /^[A-Za-z0-9_-]+$/
const NOT_A_LICENCE = 'Este archivo no es una licencia.'
const DAMAGED = 'La licencia está dañada.'

/** Checks a licence file's signature and contents. */
export function checkLicense(text: string, publicKeyPem: string): LicenseCheck {
    if (!publicKeyPem.trim()) {
        return { ok: false, reason: 'Esta copia de la aplicación no puede comprobar licencias. Contacta con tu proveedor.' }
    }
    const token = text.trim()
    if (token.length > MAX_LICENSE_CHARS || !token.startsWith(LICENSE_PREFIX)) {
        return { ok: false, reason: NOT_A_LICENCE }
    }
    const parts = token.slice(LICENSE_PREFIX.length).split('.')
    if (parts.length !== 2 || !parts.every((part) => base64url.test(part))) {
        return { ok: false, reason: NOT_A_LICENCE }
    }
    const [payload, signature] = parts

    let valid = false
    try {
        const key = createPublicKey(publicKeyPem)
        const signed = Buffer.from(LICENSE_PREFIX + payload, 'utf-8')
        valid = verify(null, signed, key, Buffer.from(signature, 'base64url'))
    } catch {
        valid = false
    }
    if (!valid) {
        return { ok: false, reason: 'La licencia no es válida para esta aplicación (puede que se haya modificado).' }
    }

    let details: unknown
    try {
        details = JSON.parse(Buffer.from(payload, 'base64url').toString('utf-8'))
    } catch {
        return { ok: false, reason: DAMAGED }
    }
    if (!isLicenseDetails(details)) {
        return { ok: false, reason: DAMAGED }
    }
    return { ok: true, details }
}

function isLicenseDetails(value: unknown): value is LicenseDetails {
    if (typeof value !== 'object' || value === null) return false
    const d = value as Record<string, unknown>
    return typeof d.id === 'string' && d.id.length > 0 && d.id.length <= 100
        && typeof d.customer === 'string' && d.customer.length > 0 && d.customer.length <= 200
        && typeof d.seats === 'number' && Number.isInteger(d.seats) && d.seats > 0
        && typeof d.issuedAt === 'string' && isDate(d.issuedAt)
        && (d.expiresAt === null || (typeof d.expiresAt === 'string' && isDate(d.expiresAt)))
}

function isDate(value: string): boolean {
    return DATE_PATTERN.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`))
}

/** First moment after the last valid day (dates are whole days in UTC). */
function endOfDay(date: string): number {
    return Date.parse(`${date}T00:00:00Z`) + DAY_MS
}

/**
 * What the user may do now.
 * @param license      details of the installed, already checked licence, if any
 * @param trialStarted when the app first ran on this computer
 */
export function licenseState(license: LicenseDetails | null, trialStarted: Date, now: Date): LicenseState {
    if (license) {
        if (license.expiresAt === null) return { kind: 'licensed', details: license, daysLeft: null }
        const remaining = endOfDay(license.expiresAt) - now.getTime()
        return remaining > 0
            ? { kind: 'licensed', details: license, daysLeft: Math.ceil(remaining / DAY_MS) }
            : { kind: 'expired', details: license }
    }
    const trialEnd = trialStarted.getTime() + TRIAL_DAYS * DAY_MS
    const remaining = trialEnd - now.getTime()
    const at = new Date(trialEnd).toISOString()
    return remaining > 0
        ? { kind: 'trial', daysLeft: Math.ceil(remaining / DAY_MS), endsAt: at }
        : { kind: 'trial-ended', endedAt: at }
}

export function canUpdateIndex(state: LicenseState): boolean {
    return state.kind === 'trial' || state.kind === 'licensed'
}

export function indexingLockedMessage(state: LicenseState): string {
    const what = state.kind === 'trial-ended' ? 'El periodo de prueba ha terminado' : 'La licencia ha caducado'
    return `${what} y el índice ya no se puede actualizar. Puedes seguir buscando. Instala una licencia en Configuración.`
}
