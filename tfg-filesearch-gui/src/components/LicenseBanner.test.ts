import { describe, expect, it } from 'vitest';
import { licenseNotice } from './LicenseBanner';

const details = { id: 'FS-1', customer: 'Despacho', seats: 2, issuedAt: '2026-01-01', expiresAt: '2027-01-01' };

describe('licenseNotice', () => {
    it('stays quiet early in the trial and with a licence far from its end', () => {
        expect(licenseNotice({ kind: 'trial', daysLeft: 20, endsAt: '' })).toBeNull();
        expect(licenseNotice({ kind: 'licensed', details, daysLeft: 200 })).toBeNull();
        expect(licenseNotice({ kind: 'licensed', details: { ...details, expiresAt: null }, daysLeft: null })).toBeNull();
    });

    it('warns before the end and says what still works afterwards', () => {
        expect(licenseNotice({ kind: 'trial', daysLeft: 1, endsAt: '' })).toEqual({ text: 'Versión de prueba: queda 1 día.', blocking: false });
        expect(licenseNotice({ kind: 'licensed', details, daysLeft: 10 })?.blocking).toBe(false);
        expect(licenseNotice({ kind: 'trial-ended', endedAt: '' })?.text).toContain('Puedes seguir buscando');
        expect(licenseNotice({ kind: 'expired', details })?.blocking).toBe(true);
    });
});
