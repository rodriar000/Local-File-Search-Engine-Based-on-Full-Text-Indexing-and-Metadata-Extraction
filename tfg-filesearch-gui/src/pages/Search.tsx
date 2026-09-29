import React, { useState } from 'react';
import { SearchInput } from '../components/search/SearchInput';
import { ResultsList } from '../components/search/ResultsList';
import { useSearch } from '../hooks/useSearch';
import { Filter, Download } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { cn } from '../lib/utils';
import { stripHighlight } from '../shared/highlight';
import { DocumentPreview } from '../components/search/DocumentPreview';

/** Filters by kind of data; DNI and NIE go together because both identify a person. */
const DATA_TYPE_FILTERS = [
    { label: 'DNI o NIE', keys: ['dni', 'nie'] },
    { label: 'IBAN', keys: ['iban'] },
    { label: 'Teléfono', keys: ['telefono'] },
    { label: 'Correo', keys: ['email'] },
    { label: 'Datos de salud', keys: ['salud'] },
];

export const SearchPage: React.FC = () => {
    const {
        query, setQuery,
        filters, setFilters,
        results, loading, error
    } = useSearch();

    const [showFilters, setShowFilters] = useState(false);
    const [openError, setOpenError] = useState<string | null>(null);
    const [previewPath, setPreviewPath] = useState<string | null>(null);

    const openResult = async (path: string) => {
        if (!window.electronAPI) return;
        setOpenError(null);
        const failure = await window.electronAPI.openPath(path);
        if (failure) setOpenError(failure);
    };

    const toggleExtension = (ext: string) => {
        const current = filters.extensions;
        const next = current.includes(ext)
            ? current.filter(e => e !== ext)
            : [...current, ext];
        setFilters({ ...filters, extensions: next });
    };

    const handleExport = async (type: 'json' | 'csv') => {
        if (!results || results.hits.length === 0) return;

        let data = '';
        if (type === 'json') {
            data = JSON.stringify(results, (_key, value) => typeof value === 'string' ? stripHighlight(value) : value, 2);
        } else {
            // Simple CSV implementation
            const headers = ['Relevancia', 'Título', 'Ruta', 'Tamaño', 'Fecha', 'Fragmento'];
            const rows = results.hits.map(h => {
                const snippet = stripHighlight(h.highlight?.content?.[0] || h.document.content || '').substring(0, 100).replace(/\n/g, ' ');
                return [
                    h.score.toFixed(2),
                    `"${(h.document.title || h.document.filename || '').replace(/"/g, '""')}"`,
                    `"${(h.document.path || '').replace(/"/g, '""')}"`,
                    h.document.size,
                    h.document.modified_at || '',
                    `"${snippet.replace(/"/g, '""')}"`
                ].join(',');
            });
            data = [headers.join(','), ...rows].join('\n');
        }

        if (window.electronAPI) {
            await window.electronAPI.saveExport({
                type,
                data,
                defaultPath: `resultados-busqueda-${new Date().toISOString().split('T')[0]}.${type}`
            });
        }
    };

    const extensions = ['pdf', 'docx', 'doc', 'msg', 'eml', 'zip', 'txt'];

    const activeFilters = filters.extensions.length + (filters.identifier?.trim() ? 1 : 0)
        + DATA_TYPE_FILTERS.filter(({ keys }) => keys.every(key => filters.dataTypes?.includes(key))).length;

    const toggleDataType = (keys: string[]) => {
        const current = filters.dataTypes ?? [];
        const selected = keys.every(key => current.includes(key));
        const next = selected ? current.filter(key => !keys.includes(key)) : [...current, ...keys];
        setFilters({ ...filters, dataTypes: next });
    };

    return (
        <div className="h-full flex flex-col">
            <div className="mb-8 space-y-4">
                <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Buscar</h1>

                <div className="flex gap-4">
                    <div className="flex-1">
                        <SearchInput
                            value={query}
                            onChange={setQuery}
                            onClear={() => setQuery('')}
                            loading={loading}
                            className="shadow-sm"
                        />
                    </div>

                    {results && results.hits.length > 0 && (
                        <div className="flex gap-2">
                            <button
                                onClick={() => handleExport('json')}
                                className="px-3 py-2 rounded-xl border border-gray-200 bg-white hover:bg-gray-50 text-gray-600 dark:bg-gray-800 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-700 transition-colors flex items-center gap-2 text-sm font-medium"
                                title="Exportar resultados como JSON"
                            >
                                <Download className="w-4 h-4" />
                                <span className="hidden sm:inline">JSON</span>
                            </button>
                            <button
                                onClick={() => handleExport('csv')}
                                className="px-3 py-2 rounded-xl border border-gray-200 bg-white hover:bg-gray-50 text-gray-600 dark:bg-gray-800 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-700 transition-colors flex items-center gap-2 text-sm font-medium"
                                title="Exportar resultados como CSV (Excel)"
                            >
                                <Download className="w-4 h-4" />
                                <span className="hidden sm:inline">CSV</span>
                            </button>
                        </div>
                    )}

                    <button
                        onClick={() => setShowFilters(!showFilters)}
                        className={cn(
                            "px-4 py-2 rounded-xl border flex items-center gap-2 transition-colors",
                            showFilters
                                ? "bg-blue-50 border-blue-200 text-blue-600 dark:bg-blue-900/20 dark:border-blue-800 dark:text-blue-400"
                                : "bg-white border-gray-200 text-gray-600 dark:bg-gray-800 dark:border-gray-700 dark:text-gray-300"
                        )}
                    >
                        <Filter className="w-4 h-4" />
                        Filtros
                        {activeFilters > 0 && (
                            <span className="ml-1 px-1.5 rounded-full bg-blue-600 text-white text-xs" aria-label={`${activeFilters} activos`}>{activeFilters}</span>
                        )}
                    </button>
                </div>

                <AnimatePresence>
                    {showFilters && (
                        <motion.div
                            initial={{ height: 0, opacity: 0 }}
                            animate={{ height: 'auto', opacity: 1 }}
                            exit={{ height: 0, opacity: 0 }}
                            className="overflow-hidden"
                        >
                            <div className="p-4 bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700">
                                <h3 className="text-sm font-medium text-gray-900 dark:text-white mb-3">Tipo de archivo</h3>
                                <div className="flex flex-wrap gap-2">
                                    {extensions.map(ext => (
                                        <button
                                            key={ext}
                                            onClick={() => toggleExtension(ext)}
                                            className={cn(
                                                "px-3 py-1.5 rounded-lg text-sm font-medium transition-colors border",
                                                filters.extensions.includes(ext)
                                                    ? "bg-blue-600 text-white border-blue-600"
                                                    : "bg-gray-50 text-gray-600 border-gray-200 hover:bg-gray-100 dark:bg-gray-700 dark:text-gray-300 dark:border-gray-600"
                                            )}
                                        >
                                            {ext.toUpperCase()}
                                        </button>
                                    ))}
                                </div>

                                <h3 className="text-sm font-medium text-gray-900 dark:text-white mt-5 mb-2">Contiene el dato</h3>
                                <input
                                    type="text"
                                    value={filters.identifier ?? ''}
                                    onChange={e => setFilters({ ...filters, identifier: e.target.value })}
                                    placeholder="DNI, NIE, CIF, IBAN, teléfono, correo o nº de procedimiento (456/2024)"
                                    aria-label="Contiene el dato"
                                    maxLength={100}
                                    className="w-full max-w-xl px-3 py-2 rounded-lg border border-gray-200 bg-gray-50 text-sm dark:bg-gray-700 dark:border-gray-600 dark:text-white"
                                />
                                <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">Da igual cómo esté escrito: 12.345.678-Z y 12345678z son el mismo DNI.</p>

                                <h3 className="text-sm font-medium text-gray-900 dark:text-white mt-5 mb-2">Documentos con datos personales</h3>
                                <div className="flex flex-wrap gap-2">
                                    {DATA_TYPE_FILTERS.map(({ label, keys }) => {
                                        const selected = keys.every(key => filters.dataTypes?.includes(key));
                                        return (
                                            <button
                                                key={label}
                                                onClick={() => toggleDataType(keys)}
                                                aria-pressed={selected}
                                                className={cn(
                                                    "px-3 py-1.5 rounded-lg text-sm font-medium transition-colors border",
                                                    selected
                                                        ? "bg-rose-600 text-white border-rose-600"
                                                        : "bg-gray-50 text-gray-600 border-gray-200 hover:bg-gray-100 dark:bg-gray-700 dark:text-gray-300 dark:border-gray-600"
                                                )}
                                            >
                                                {label}
                                            </button>
                                        );
                                    })}
                                </div>
                            </div>
                        </motion.div>
                    )}
                </AnimatePresence>
            </div>

            {openError && (
                <div role="alert" className="p-4 mb-6 bg-red-50 text-red-600 rounded-xl border border-red-200 dark:bg-red-900/20 dark:text-red-400 dark:border-red-800">
                    {openError}
                </div>
            )}

            {error && (
                <div className="p-4 mb-6 bg-red-50 text-red-600 rounded-xl border border-red-200 dark:bg-red-900/20 dark:text-red-400 dark:border-red-800">
                    {error}
                </div>
            )}

            <ResultsList
                results={results}
                loading={loading}
                onResultClick={(hit) => setPreviewPath(hit.document.path)}
            />

            {previewPath && (
                <DocumentPreview
                    path={previewPath}
                    query={query}
                    onClose={() => setPreviewPath(null)}
                    onOpen={openResult}
                />
            )}
        </div>
    );
};
