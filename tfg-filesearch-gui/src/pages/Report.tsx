import React, { useState } from 'react';
import { Download, FileText, ShieldCheck } from 'lucide-react';
import { getPersonalDataReport } from '../services/searchApi';
import { dataTypeLabel, PersonalDataReport } from '../shared/personalData';
import { DocumentPreview } from '../components/search/DocumentPreview';

const inputClass = 'w-full px-3 py-2 rounded-lg border border-gray-200 bg-white text-sm dark:bg-gray-800 dark:border-gray-700 dark:text-white';

/**
 * Data protection requests (RGPD arts. 15 and 17): every document that
 * mentions a person, by full name and/or identifier, to review and export.
 */
export const ReportPage: React.FC = () => {
    const [name, setName] = useState('');
    const [identifier, setIdentifier] = useState('');
    const [report, setReport] = useState<PersonalDataReport | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [saved, setSaved] = useState<string | null>(null);
    const [previewPath, setPreviewPath] = useState<string | null>(null);

    const canSearch = name.trim().length >= 3 || identifier.trim().length > 0;

    const generate = async (event: React.FormEvent) => {
        event.preventDefault();
        if (!canSearch || loading) return;
        setLoading(true);
        setError(null);
        setSaved(null);
        try {
            setReport(await getPersonalDataReport(name, identifier));
        } catch (err) {
            setReport(null);
            setError(err instanceof Error ? err.message : String(err));
        } finally {
            setLoading(false);
        }
    };

    const exportReport = async (format: 'csv' | 'pdf') => {
        if (!report || !window.electronAPI) return;
        setError(null);
        try {
            const file = await window.electronAPI.exportReport({ format, report });
            if (file) setSaved(file);
        } catch {
            setError('No se pudo guardar el informe. Prueba en otra carpeta.');
        }
    };

    // Highlight the name or the identifier in the preview.
    const previewQuery = report?.name ? `"${report.name}"` : (report?.identifier ?? '');

    return (
        <div className="h-full flex flex-col max-w-5xl">
            <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Informe RGPD</h1>
            <p className="mt-1 mb-6 text-sm text-gray-600 dark:text-gray-400">
                Encuentra todos los documentos en los que aparece una persona, para responder a una solicitud de acceso o de supresión.
            </p>

            <form onSubmit={generate} className="p-5 bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 grid gap-4 md:grid-cols-[1fr_1fr_auto] items-end">
                <label className="text-sm font-medium text-gray-900 dark:text-white">
                    Nombre y apellidos
                    <input value={name} onChange={e => setName(e.target.value)} maxLength={200} placeholder="Juan Pérez García" className={`${inputClass} mt-1`} />
                </label>
                <label className="text-sm font-medium text-gray-900 dark:text-white">
                    DNI, NIE u otro identificador
                    <input value={identifier} onChange={e => setIdentifier(e.target.value)} maxLength={100} placeholder="12345678Z" className={`${inputClass} mt-1`} />
                </label>
                <button
                    type="submit"
                    disabled={!canSearch || loading}
                    className="flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
                >
                    <ShieldCheck className="w-4 h-4" />
                    {loading ? 'Buscando…' : 'Buscar documentos'}
                </button>
                <p className="md:col-span-3 text-xs text-gray-500 dark:text-gray-400">
                    Se buscan el nombre completo exacto (sin distinguir mayúsculas ni tildes) y el identificador escrito de cualquier forma.
                    Los documentos sin texto legible no pueden aparecer.
                </p>
            </form>

            {error && (
                <div role="alert" className="mt-6 p-4 bg-red-50 text-red-600 rounded-xl border border-red-200 dark:bg-red-900/20 dark:text-red-400 dark:border-red-800">
                    {error}
                </div>
            )}

            {report && (
                <section className="mt-6 flex-1 min-h-0 flex flex-col" aria-label="Resultado del informe">
                    <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
                        <p className="text-sm text-gray-700 dark:text-gray-300">
                            <strong>{report.total.toLocaleString('es-ES')}</strong> {report.total === 1 ? 'documento menciona' : 'documentos mencionan'} a esta persona
                            {report.truncated && ` (se muestran los ${report.documents.length.toLocaleString('es-ES')} primeros)`}.
                        </p>
                        <div className="flex gap-2">
                            {(['pdf', 'csv'] as const).map(format => (
                                <button
                                    key={format}
                                    onClick={() => exportReport(format)}
                                    className="px-3 py-2 rounded-lg border border-gray-200 bg-white hover:bg-gray-50 text-gray-700 dark:bg-gray-800 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-700 flex items-center gap-2 text-sm font-medium"
                                >
                                    <Download className="w-4 h-4" />
                                    Guardar {format.toUpperCase()}
                                </button>
                            ))}
                        </div>
                    </div>
                    {saved && <p className="mb-3 text-sm text-green-700 dark:text-green-400">Informe guardado en {saved}</p>}

                    {report.documents.length === 0 ? (
                        <p className="text-sm text-gray-500 dark:text-gray-400">No hay documentos indexados que mencionen a esta persona.</p>
                    ) : (
                        <div className="overflow-auto rounded-xl border border-gray-100 dark:border-gray-700 bg-white dark:bg-gray-800">
                            <table className="w-full text-sm">
                                <thead className="text-left text-gray-500 dark:text-gray-400 border-b border-gray-100 dark:border-gray-700">
                                    <tr>
                                        <th className="px-4 py-2 font-medium">Documento</th>
                                        <th className="px-4 py-2 font-medium">Modificado</th>
                                        <th className="px-4 py-2 font-medium">Encontrado por</th>
                                        <th className="px-4 py-2 font-medium">Datos que contiene</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {report.documents.map(doc => (
                                        <tr
                                            key={doc.path}
                                            onClick={() => setPreviewPath(doc.path)}
                                            className="border-b border-gray-50 dark:border-gray-700/50 hover:bg-gray-50 dark:hover:bg-gray-700/40 cursor-pointer"
                                        >
                                            <td className="px-4 py-2 max-w-md">
                                                <div className="flex items-center gap-2 font-medium text-blue-600 dark:text-blue-400">
                                                    <FileText className="w-4 h-4 shrink-0" />
                                                    <span className="truncate" title={doc.filename}>{doc.filename}</span>
                                                </div>
                                                <div className="text-xs text-gray-500 dark:text-gray-400 font-mono truncate" title={doc.path}>{doc.path}</div>
                                            </td>
                                            <td className="px-4 py-2 whitespace-nowrap text-gray-600 dark:text-gray-300">
                                                {doc.modifiedAt ? new Date(doc.modifiedAt).toLocaleDateString('es-ES') : ''}
                                            </td>
                                            <td className="px-4 py-2 text-gray-600 dark:text-gray-300">
                                                {[doc.byName && 'Nombre', doc.byIdentifier && 'Identificador'].filter(Boolean).join(' y ')}
                                            </td>
                                            <td className="px-4 py-2 text-gray-600 dark:text-gray-300">
                                                {doc.dataTypes.map(dataTypeLabel).join(', ')}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>
            )}

            {previewPath && (
                <DocumentPreview
                    path={previewPath}
                    query={previewQuery}
                    onClose={() => setPreviewPath(null)}
                    onOpen={async (path) => {
                        const failure = await window.electronAPI?.openPath(path);
                        if (failure) setError(failure);
                    }}
                />
            )}
        </div>
    );
};
