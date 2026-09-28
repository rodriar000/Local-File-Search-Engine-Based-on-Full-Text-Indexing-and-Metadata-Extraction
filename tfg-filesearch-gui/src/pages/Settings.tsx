import React, { useCallback, useEffect, useState } from 'react';
import { RefreshCw, Database, FolderOpen, XCircle, ShieldCheck, KeyRound, LifeBuoy } from 'lucide-react';
import { useAppStore } from '../store/useAppStore';
import { cn } from '../lib/utils';
import { cancelIndexing, getIndexStatus, startIndexing } from '../services/searchApi';
import { IndexStatus, LicenseState } from '../types';
import { useLicenseStore } from '../store/useLicenseStore';
import { IndexingProgress } from '../components/IndexingProgress';

const POLL_INTERVAL_MS = 500;
const IDLE_POLL_INTERVAL_MS = 3000;

export const SettingsPage: React.FC = () => {
    const { indexFolder, setIndexFolder } = useAppStore();
    const [status, setStatus] = useState<IndexStatus | null>(null);
    const [error, setError] = useState<string | null>(null);
    const running = status?.running ?? false;

    const refreshStatus = useCallback(async () => {
        try {
            setStatus(await getIndexStatus());
        } catch (err) {
            setError(err instanceof Error ? err.message : String(err));
        }
    }, []);

    // Follow the engine's state while the page is open: quickly during a run, and
    // slowly otherwise so a run started elsewhere (e.g. the sync at launch) shows up.
    useEffect(() => {
        if (!window.electronAPI) return;
        refreshStatus();
        const timer = setInterval(refreshStatus, running ? POLL_INTERVAL_MS : IDLE_POLL_INTERVAL_MS);
        return () => clearInterval(timer);
    }, [running, refreshStatus]);

    const handleChooseFolder = async () => {
        if (!window.electronAPI) return;
        const folder = await window.electronAPI.selectFolder();
        if (folder) setIndexFolder(folder);
    };

    const handleIndex = async () => {
        if (!indexFolder || running) return;
        setError(null);
        try {
            setStatus(await startIndexing(indexFolder));
        } catch (err) {
            setError(err instanceof Error ? err.message : String(err));
            refreshStatus();
        }
    };

    const { license, setLicense, refresh: refreshLicense } = useLicenseStore();
    const [licenseMessage, setLicenseMessage] = useState<{ text: string; error: boolean } | null>(null);

    useEffect(() => {
        refreshLicense();
    }, [refreshLicense]);

    const handleInstallLicense = async () => {
        if (!window.electronAPI) return;
        setLicenseMessage(null);
        try {
            const result = await window.electronAPI.installLicense();
            setLicense(result.state);
            if (result.error) setLicenseMessage({ text: result.error, error: true });
            else if (result.installed) setLicenseMessage({ text: 'Licencia instalada.', error: false });
        } catch (err) {
            setLicenseMessage({ text: err instanceof Error ? err.message : String(err), error: true });
        }
    };

    const [supportInfo, setSupportInfo] = useState<string | null>(null);

    const handleCopySupportInfo = async () => {
        if (!window.electronAPI) return;
        try {
            setSupportInfo(await window.electronAPI.copySupportInfo());
        } catch (err) {
            setSupportInfo(err instanceof Error ? err.message : String(err));
        }
    };

    const handleCancel = async () => {
        try {
            setStatus(await cancelIndexing());
        } catch (err) {
            setError(err instanceof Error ? err.message : String(err));
        }
    };

    return (
        <div className="max-w-3xl mx-auto space-y-8">
            <div>
                <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Configuración</h1>
                <p className="text-gray-500 dark:text-gray-400">Elige los documentos en los que buscar</p>
            </div>

            <section className="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-6 shadow-sm">
                <div className="flex items-center gap-3 mb-6">
                    <div className="p-2 bg-orange-50 dark:bg-orange-900/20 rounded-lg text-orange-600 dark:text-orange-400">
                        <Database className="w-5 h-5" />
                    </div>
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Documentos</h2>
                </div>

                <div className="space-y-6">
                    <div className="flex items-center justify-between gap-4 p-4 bg-gray-50 dark:bg-gray-700/30 rounded-lg border border-gray-100 dark:border-gray-700">
                        <div className="min-w-0">
                            <h3 className="font-medium text-gray-900 dark:text-white">Carpeta de documentos</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400 truncate" title={indexFolder ?? undefined}>
                                {indexFolder ?? 'Aún no has elegido ninguna carpeta'}
                            </p>
                        </div>
                        <button
                            onClick={handleChooseFolder}
                            disabled={running || !window.electronAPI}
                            className="flex shrink-0 items-center gap-2 px-4 py-2 rounded-lg transition-colors bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 dark:bg-gray-700 dark:border-gray-600 dark:text-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            <FolderOpen className="w-4 h-4" />
                            Elegir carpeta
                        </button>
                    </div>

                    <div className="flex items-center justify-between gap-4 p-4 bg-gray-50 dark:bg-gray-700/30 rounded-lg border border-gray-100 dark:border-gray-700">
                        <div>
                            <h3 className="font-medium text-gray-900 dark:text-white">Actualizar índice</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                Lee los documentos nuevos y modificados y olvida los borrados. Los que no han cambiado no se vuelven a leer.
                            </p>
                        </div>
                        {running ? (
                            <button
                                onClick={handleCancel}
                                className="flex shrink-0 items-center gap-2 px-4 py-2 rounded-lg transition-colors bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 dark:bg-gray-700 dark:border-gray-600 dark:text-gray-200"
                            >
                                <XCircle className="w-4 h-4" />
                                Cancelar
                            </button>
                        ) : (
                            <button
                                onClick={handleIndex}
                                disabled={!indexFolder || !window.electronAPI}
                                className={cn(
                                    "flex shrink-0 items-center gap-2 px-4 py-2 rounded-lg transition-colors",
                                    !indexFolder || !window.electronAPI
                                        ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                                        : "bg-blue-600 text-white hover:bg-blue-700"
                                )}
                            >
                                <RefreshCw className="w-4 h-4" />
                                Actualizar ahora
                            </button>
                        )}
                    </div>

                    {error && (
                        <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>
                    )}

                    {status && <IndexingProgress status={status} />}
                </div>
            </section>

            <section className="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-6 shadow-sm">
                <div className="flex items-center gap-3 mb-6">
                    <div className="p-2 bg-blue-50 dark:bg-blue-900/20 rounded-lg text-blue-600 dark:text-blue-400">
                        <KeyRound className="w-5 h-5" />
                    </div>
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Licencia</h2>
                </div>
                <div className="flex items-center justify-between gap-4 p-4 bg-gray-50 dark:bg-gray-700/30 rounded-lg border border-gray-100 dark:border-gray-700">
                    <div className="min-w-0 text-sm text-gray-600 dark:text-gray-300">
                        <LicenseSummary license={license} />
                    </div>
                    <button
                        onClick={handleInstallLicense}
                        disabled={!window.electronAPI}
                        className="flex shrink-0 items-center gap-2 px-4 py-2 rounded-lg transition-colors bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 dark:bg-gray-700 dark:border-gray-600 dark:text-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        Instalar licencia…
                    </button>
                </div>
                {licenseMessage && (
                    <p
                        role={licenseMessage.error ? 'alert' : 'status'}
                        className={cn('mt-4 text-sm', licenseMessage.error ? 'text-red-600 dark:text-red-400' : 'text-green-700 dark:text-green-400')}
                    >
                        {licenseMessage.text}
                    </p>
                )}
            </section>

            <section className="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-6 shadow-sm">
                <div className="flex items-center justify-between gap-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-green-50 dark:bg-green-900/20 rounded-lg text-green-600 dark:text-green-400">
                            <LifeBuoy className="w-5 h-5" />
                        </div>
                        <div>
                            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Soporte</h2>
                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                Copia el estado de la aplicación para enviarlo si algo no funciona. No incluye nombres ni contenido de documentos.
                            </p>
                        </div>
                    </div>
                    <button
                        onClick={handleCopySupportInfo}
                        disabled={!window.electronAPI}
                        className="flex shrink-0 items-center gap-2 px-4 py-2 rounded-lg transition-colors bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 dark:bg-gray-700 dark:border-gray-600 dark:text-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        Copiar información
                    </button>
                </div>
                {supportInfo && (
                    <div className="mt-4 space-y-2">
                        <p role="status" className="text-sm text-green-700 dark:text-green-400">Copiado al portapapeles:</p>
                        <pre className="p-3 rounded-lg bg-gray-50 dark:bg-gray-700/30 text-xs text-gray-700 dark:text-gray-200 whitespace-pre-wrap">{supportInfo}</pre>
                    </div>
                )}
            </section>

            <section className="flex items-start gap-3 p-4 text-sm text-gray-600 dark:text-gray-300">
                <ShieldCheck className="w-5 h-5 shrink-0 text-green-600 dark:text-green-400" />
                <p>
                    Los documentos se leen e indexan solo en este ordenador. El buscador solo acepta conexiones de
                    esta aplicación y no se envía nada por la red.
                </p>
            </section>
        </div>
    );
};

const formatDate = (date: string) => new Date(date.length === 10 ? `${date}T12:00:00Z` : date).toLocaleDateString('es-ES');

const LicenseSummary: React.FC<{ license: LicenseState | null }> = ({ license }) => {
    if (!license) return <p>Comprobando licencia…</p>;
    switch (license.kind) {
        case 'trial':
            return (
                <>
                    <h3 className="font-medium text-gray-900 dark:text-white">Versión de prueba</h3>
                    <p>{license.daysLeft === 1 ? 'Queda 1 día' : `Quedan ${license.daysLeft} días`}, hasta el {formatDate(license.endsAt)}.</p>
                </>
            );
        case 'trial-ended':
            return (
                <>
                    <h3 className="font-medium text-gray-900 dark:text-white">Prueba terminada</h3>
                    <p>Puedes seguir buscando, pero el índice no se actualizará hasta que instales una licencia.</p>
                </>
            );
        case 'licensed':
        case 'expired':
            return (
                <>
                    <h3 className="font-medium text-gray-900 dark:text-white truncate" title={license.details.customer}>
                        {license.details.customer}
                    </h3>
                    <p>
                        Licencia {license.details.id} · {license.details.seats} {license.details.seats === 1 ? 'ordenador' : 'ordenadores'} ·{' '}
                        {license.details.expiresAt === null
                            ? 'sin caducidad'
                            : `${license.kind === 'expired' ? 'caducó el' : 'válida hasta el'} ${formatDate(license.details.expiresAt)}`}
                    </p>
                    {license.kind === 'expired' && (
                        <p className="text-amber-700 dark:text-amber-400">Puedes seguir buscando, pero el índice ya no se actualiza.</p>
                    )}
                </>
            );
    }
};
