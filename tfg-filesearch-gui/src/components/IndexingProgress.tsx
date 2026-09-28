import React from 'react';
import { IndexStatus } from '../types';

const FAILURES_SHOWN = 10;

/** "1 documento no tiene" / "3 documentos no tienen". */
function plural(count: number, one: string, many: string): string {
    return `${count.toLocaleString('es-ES')} ${count === 1 ? one : many}`;
}

function formatDuration(ms: number): string {
    return ms < 60_000 ? `${(ms / 1000).toLocaleString('es-ES', { maximumFractionDigits: 1 })} s` : `${Math.round(ms / 60_000)} min`;
}

/** Progress bar while indexing, then a summary of the last run. */
export const IndexingProgress: React.FC<{ status: IndexStatus }> = ({ status }) => {
    if (status.running) {
        const percent = status.total > 0 ? Math.round((status.processed / status.total) * 100) : 0;
        return (
            <div className="space-y-2" aria-live="polite">
                <div className="flex justify-between text-sm text-gray-600 dark:text-gray-300">
                    <span>{status.total > 0 ? `Leyendo documentos: ${status.processed.toLocaleString('es-ES')} de ${status.total.toLocaleString('es-ES')}` : 'Buscando documentos nuevos y modificados…'}</span>
                    <span>{percent}%</span>
                </div>
                <div className="h-2 rounded-full bg-gray-100 dark:bg-gray-700 overflow-hidden">
                    <div
                        role="progressbar"
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-valuenow={percent}
                        className="h-full bg-blue-600 transition-all"
                        style={{ width: `${percent}%` }}
                    />
                </div>
            </div>
        );
    }

    if (status.error) {
        return <p role="alert" className="text-sm text-red-600 dark:text-red-400">No se pudo indexar: {status.error}</p>;
    }

    const report = status.lastReport;
    if (!report) return null;

    const rows: [string, number][] = [
        ['Documentos en la carpeta', report.scanned],
        ['Nuevos', report.added],
        ['Actualizados', report.updated],
        ['Sin cambios', report.unchanged],
        ['Quitados del índice', report.deleted],
    ];

    return (
        <div className="space-y-3 text-sm" aria-live="polite">
            <p className="font-medium text-gray-900 dark:text-white">
                {report.cancelled ? 'Indexación cancelada' : 'Índice al día'}
                <span className="font-normal text-gray-500 dark:text-gray-400"> · {formatDuration(report.durationMs)}</span>
            </p>
            <dl className="grid grid-cols-2 sm:grid-cols-5 gap-3">
                {rows.map(([label, value]) => (
                    <div key={label} className="p-3 rounded-lg bg-gray-50 dark:bg-gray-700/30">
                        <dt className="text-gray-500 dark:text-gray-400">{label}</dt>
                        <dd className="text-lg font-semibold text-gray-900 dark:text-white">{value.toLocaleString('es-ES')}</dd>
                    </div>
                ))}
            </dl>
            {report.withoutText > 0 && (
                <p className="text-amber-700 dark:text-amber-400">
                    {plural(report.withoutText, 'documento no tiene', 'documentos no tienen')} texto legible (por ejemplo, escaneos sin OCR) y solo se encuentran por su nombre.
                </p>
            )}
            {report.skippedTooLarge > 0 && (
                <p className="text-amber-700 dark:text-amber-400">{plural(report.skippedTooLarge, 'archivo se ha omitido', 'archivos se han omitido')} por ser demasiado grandes.</p>
            )}
            {report.failed > 0 && (
                <details className="text-red-700 dark:text-red-400">
                    <summary className="cursor-pointer">{plural(report.failed, 'archivo no se ha podido leer', 'archivos no se han podido leer')}</summary>
                    <ul className="mt-2 space-y-1 text-xs text-gray-600 dark:text-gray-300">
                        {report.failures.slice(0, FAILURES_SHOWN).map((failure) => (
                            <li key={failure.path} className="break-all">
                                <span className="font-medium">{failure.path}</span>: {failure.reason}
                            </li>
                        ))}
                        {report.failed > FAILURES_SHOWN && <li>…y {report.failed - FAILURES_SHOWN} más</li>}
                    </ul>
                </details>
            )}
        </div>
    );
};
