import React, { useState } from 'react';
import { Save, RefreshCw, Database, Server, FolderOpen } from 'lucide-react';
import { useAppStore } from '../store/useAppStore';
import { motion } from 'framer-motion';
import { cn } from '../lib/utils';
import { isLoopbackHttpUrl, isValidIndexName } from '../shared/esTarget';

export const SettingsPage: React.FC = () => {
    const { config, setConfig, indexFolder, setIndexFolder } = useAppStore();
    const [localConfig, setLocalConfig] = useState(config);
    const [saveMessage, setSaveMessage] = useState<{ ok: boolean; text: string } | null>(null);
    const [reindexing, setReindexing] = useState(false);
    const [reindexOutput, setReindexOutput] = useState('');

    const handleSave = () => {
        const { url, indexName } = localConfig.elasticsearch;
        if (!isLoopbackHttpUrl(url)) {
            setSaveMessage({ ok: false, text: 'The address must point to this computer, for example http://localhost:9200.' });
            return;
        }
        if (!isValidIndexName(indexName)) {
            setSaveMessage({ ok: false, text: 'Index names use lowercase letters, digits, "-" and "_", and cannot start with "-" or "_".' });
            return;
        }
        setConfig(localConfig);
        setSaveMessage({ ok: true, text: 'Settings saved.' });
    };

    const handleChooseFolder = async () => {
        if (!window.electronAPI) return;
        const folder = await window.electronAPI.selectFolder();
        if (folder) setIndexFolder(folder);
    };

    const handleReindex = async () => {
        if (!window.electronAPI) {
            setReindexOutput('Indexing requires the desktop application.');
            return;
        }
        if (!indexFolder || reindexing) return;
        setReindexing(true);
        setReindexOutput(`Indexing ${indexFolder}...\n`);
        try {
            const output = await window.electronAPI.reindex(indexFolder);
            setReindexOutput(prev => prev + output + '\nDone.');
        } catch (error) {
            // Electron prefixes errors thrown in the main process; show only our message.
            const message = error instanceof Error ? error.message.replace(/^Error invoking remote method '[^']+': (Error: )?/, '') : String(error);
            setReindexOutput(prev => prev + 'Error: ' + message);
        } finally {
            setReindexing(false);
        }
    };

    // Safe access to nested config
    const esConfig = localConfig?.elasticsearch || { url: '', indexName: '' };

    return (
        <div className="max-w-3xl mx-auto space-y-8">
            <div>
                <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Settings</h1>
                <p className="text-gray-500 dark:text-gray-400">Configure your search engine and index</p>
            </div>

            {/* Connection Settings */}
            <section className="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-6 shadow-sm">
                <div className="flex items-center gap-3 mb-6">
                    <div className="p-2 bg-blue-50 dark:bg-blue-900/20 rounded-lg text-blue-600 dark:text-blue-400">
                        <Server className="w-5 h-5" />
                    </div>
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Elasticsearch Connection</h2>
                </div>

                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Host URL</label>
                        <input
                            type="text"
                            value={esConfig.url}
                            onChange={e => setLocalConfig({
                                ...localConfig,
                                elasticsearch: {
                                    ...esConfig,
                                    url: e.target.value
                                }
                            })}
                            className="w-full px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 outline-none"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Index Name</label>
                        <input
                            type="text"
                            value={esConfig.indexName}
                            onChange={e => setLocalConfig({
                                ...localConfig,
                                elasticsearch: {
                                    ...esConfig,
                                    indexName: e.target.value
                                }
                            })}
                            className="w-full px-4 py-2 rounded-lg border border-gray-200 dark:border-gray-600 bg-gray-50 dark:bg-gray-700 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500 outline-none"
                        />
                    </div>
                    <div className="flex items-center justify-end gap-4">
                        {saveMessage && (
                            <p role="status" className={cn("text-sm", saveMessage.ok ? "text-green-600 dark:text-green-400" : "text-red-600 dark:text-red-400")}>
                                {saveMessage.text}
                            </p>
                        )}
                        <button
                            onClick={handleSave}
                            className="flex shrink-0 items-center gap-2 whitespace-nowrap px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                        >
                            <Save className="w-4 h-4" />
                            Save Changes
                        </button>
                    </div>
                </div>
            </section>

            {/* Index Management */}
            <section className="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-6 shadow-sm">
                <div className="flex items-center gap-3 mb-6">
                    <div className="p-2 bg-orange-50 dark:bg-orange-900/20 rounded-lg text-orange-600 dark:text-orange-400">
                        <Database className="w-5 h-5" />
                    </div>
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Index Management</h2>
                </div>

                <div className="space-y-6">
                    <div className="flex items-center justify-between gap-4 p-4 bg-gray-50 dark:bg-gray-700/30 rounded-lg border border-gray-100 dark:border-gray-700">
                        <div className="min-w-0">
                            <h3 className="font-medium text-gray-900 dark:text-white">Documents folder</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400 truncate" title={indexFolder ?? undefined}>
                                {indexFolder ?? 'No folder selected yet'}
                            </p>
                        </div>
                        <button
                            onClick={handleChooseFolder}
                            disabled={reindexing || !window.electronAPI}
                            className="flex items-center gap-2 px-4 py-2 rounded-lg transition-colors bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 dark:bg-gray-700 dark:border-gray-600 dark:text-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            <FolderOpen className="w-4 h-4" />
                            Choose Folder
                        </button>
                    </div>

                    <div className="flex items-center justify-between gap-4 p-4 bg-gray-50 dark:bg-gray-700/30 rounded-lg border border-gray-100 dark:border-gray-700">
                        <div>
                            <h3 className="font-medium text-gray-900 dark:text-white">Index Documents</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400">Extract and index every supported document in the folder</p>
                        </div>
                        <button
                            onClick={handleReindex}
                            disabled={reindexing || !indexFolder}
                            className={cn(
                                "flex items-center gap-2 px-4 py-2 rounded-lg transition-colors",
                                reindexing || !indexFolder
                                    ? "bg-gray-100 text-gray-400 cursor-not-allowed"
                                    : "bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 dark:bg-gray-700 dark:border-gray-600 dark:text-gray-200"
                            )}
                        >
                            <RefreshCw className={cn("w-4 h-4", reindexing && "animate-spin")} />
                            {reindexing ? 'Indexing...' : 'Index Now'}
                        </button>
                    </div>

                    {reindexOutput && (
                        <motion.div
                            initial={{ opacity: 0, height: 0 }}
                            animate={{ opacity: 1, height: 'auto' }}
                            className="bg-black text-green-400 p-4 rounded-lg font-mono text-xs overflow-x-auto max-h-60"
                        >
                            <pre className="whitespace-pre-wrap">{reindexOutput}</pre>
                        </motion.div>
                    )}
                </div>
            </section>
        </div>
    );
};
