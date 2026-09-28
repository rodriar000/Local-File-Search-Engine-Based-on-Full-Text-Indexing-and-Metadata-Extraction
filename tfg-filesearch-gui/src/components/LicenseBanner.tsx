import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { KeyRound } from 'lucide-react';
import { useLicenseStore } from '../store/useLicenseStore';
import { LicenseState } from '../types';

const TRIAL_WARNING_DAYS = 7;
const RENEWAL_WARNING_DAYS = 30;

/** What to tell the user about the licence, or null when there is nothing to say. */
export function licenseNotice(license: LicenseState | null): { text: string; blocking: boolean } | null {
    if (!license) return null;
    switch (license.kind) {
        case 'trial':
            return license.daysLeft <= TRIAL_WARNING_DAYS
                ? { text: `Versión de prueba: ${license.daysLeft === 1 ? 'queda 1 día' : `quedan ${license.daysLeft} días`}.`, blocking: false }
                : null;
        case 'trial-ended':
            return { text: 'El periodo de prueba ha terminado. Puedes seguir buscando, pero el índice ya no se actualiza.', blocking: true };
        case 'expired':
            return { text: 'La licencia ha caducado. Puedes seguir buscando, pero el índice ya no se actualiza.', blocking: true };
        case 'licensed':
            return license.daysLeft !== null && license.daysLeft <= RENEWAL_WARNING_DAYS
                ? { text: `La licencia caduca en ${license.daysLeft === 1 ? '1 día' : `${license.daysLeft} días`}.`, blocking: false }
                : null;
    }
}

export const LicenseBanner: React.FC = () => {
    const { license, refresh } = useLicenseStore();

    useEffect(() => {
        refresh();
    }, [refresh]);

    const notice = licenseNotice(license);
    if (!notice) return null;

    return (
        <div
            role="status"
            className={
                notice.blocking
                    ? 'flex items-center gap-3 px-8 py-2 text-sm bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-100'
                    : 'flex items-center gap-3 px-8 py-2 text-sm bg-blue-50 text-blue-900 dark:bg-blue-900/30 dark:text-blue-100'
            }
        >
            <KeyRound className="w-4 h-4 shrink-0" />
            <span className="flex-1">{notice.text}</span>
            <Link to="/settings" className="font-medium underline underline-offset-2">Instalar licencia</Link>
        </div>
    );
};
