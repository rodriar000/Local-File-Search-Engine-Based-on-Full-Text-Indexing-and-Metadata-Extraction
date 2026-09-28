import { generateKeyPairSync } from 'node:crypto'
import { describe, expect, it } from 'vitest'
import { canUpdateIndex, checkLicense, indexingLockedMessage, LicenseDetails, licenseState, TRIAL_DAYS } from './license'
// @ts-expect-error: plain JavaScript tool without type declarations
import { issueLicense, publicKeyFileContents } from '../scripts/license.mjs'

const keys = generateKeyPairSync('ed25519')
const privateKey = keys.privateKey.export({ type: 'pkcs8', format: 'pem' }) as string
const publicKey = keys.publicKey.export({ type: 'spki', format: 'pem' }) as string

const details: LicenseDetails = {
    id: 'FS-2026-001',
    customer: 'Despacho Pérez Abogados',
    seats: 3,
    issuedAt: '2026-09-28',
    expiresAt: '2027-09-30',
}

const day = 24 * 60 * 60 * 1000

describe('checkLicense', () => {
    it('accepts a licence signed with the matching key', () => {
        expect(checkLicense(`\n${issueLicense(privateKey, details)}\n`, publicKey)).toEqual({ ok: true, details })
    })

    it('rejects altered licences', () => {
        const licence: string = issueLicense(privateKey, details)
        const [, payload, signature] = licence.split('.')
        const altered = Buffer.from(JSON.stringify({ ...details, seats: 50 })).toString('base64url')
        expect(checkLicense(`FSL1.${altered}.${signature}`, publicKey).ok).toBe(false)
        expect(checkLicense(`FSL1.${payload}.${signature.slice(0, -4)}AAAA`, publicKey).ok).toBe(false)
    })

    it('rejects licences signed with another key', () => {
        const other = generateKeyPairSync('ed25519').privateKey.export({ type: 'pkcs8', format: 'pem' })
        expect(checkLicense(issueLicense(other, details), publicKey)).toMatchObject({ ok: false })
    })

    it('rejects files that are not licences or have bad details', () => {
        expect(checkLicense('hello', publicKey)).toEqual({ ok: false, reason: 'Este archivo no es una licencia.' })
        expect(checkLicense('FSL1.a.b.c', publicKey).ok).toBe(false)
        expect(checkLicense('FSL1.' + 'a'.repeat(5000) + '.b', publicKey).ok).toBe(false)
        expect(checkLicense(issueLicense(privateKey, { ...details, seats: 0 }), publicKey).ok).toBe(false)
        expect(checkLicense(issueLicense(privateKey, { ...details, expiresAt: '30/09/2027' }), publicKey).ok).toBe(false)
    })

    it('cannot verify anything without a built-in key', () => {
        expect(checkLicense(issueLicense(privateKey, details), '').ok).toBe(false)
    })
})

describe('licenseState', () => {
    const started = new Date('2026-09-01T10:00:00Z')

    it('runs a trial from the first launch', () => {
        const state = licenseState(null, started, new Date(started.getTime() + 10 * day))
        expect(state).toMatchObject({ kind: 'trial', daysLeft: TRIAL_DAYS - 10 })
        expect(canUpdateIndex(state)).toBe(true)
    })

    it('stops updating the index when the trial ends', () => {
        const state = licenseState(null, started, new Date(started.getTime() + TRIAL_DAYS * day))
        expect(state.kind).toBe('trial-ended')
        expect(canUpdateIndex(state)).toBe(false)
        expect(indexingLockedMessage(state)).toContain('periodo de prueba ha terminado')
    })

    it('is valid through the last day of the licence', () => {
        expect(licenseState(details, started, new Date('2027-09-30T23:00:00Z'))).toMatchObject({ kind: 'licensed', daysLeft: 1 })
        const expired = licenseState(details, started, new Date('2027-10-01T00:00:00Z'))
        expect(expired.kind).toBe('expired')
        expect(canUpdateIndex(expired)).toBe(false)
    })

    it('never ends for a perpetual licence', () => {
        expect(licenseState({ ...details, expiresAt: null }, started, new Date('2040-01-01'))).toMatchObject({
            kind: 'licensed',
            daysLeft: null,
        })
    })
})

describe('license key file', () => {
    it('can be read back by the app', async () => {
        const source = publicKeyFileContents(publicKey)
        const match = /LICENSE_PUBLIC_KEY = (".*")/.exec(source)
        expect(JSON.parse(match![1])).toBe(publicKey)
    })
})
