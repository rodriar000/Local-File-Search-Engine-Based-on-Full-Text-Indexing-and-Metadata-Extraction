import React, { useCallback, useEffect, useState } from 'react';
import { RefreshCw, Database, FolderOpen, XCircle, ShieldCheck } from 'lucide-react';
import { useAppStore } from '../store/useAppStore';
import { cn } from '../lib/utils';
import { cancelIndexing, getIndexStatus, startIndexing } from '../services/searchApi';
import { IndexStatus } from '../types';
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
                <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Settings</h1>
                <p className="text-gray-500 dark:text-gray-400">Choose the documents to search</p>
            </div>

            <section className="bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-6 shadow-sm">
                <div className="flex items-center gap-3 mb-6">
                    <div className="p-2 bg-orange-50 dark:bg-orange-900/20 rounded-lg text-orange-600 dark:text-orange-400">
                        <Database className="w-5 h-5" />
                    </div>
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white">Documents</h2>
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
                            disabled={running || !window.electronAPI}
                            className="flex shrink-0 items-center gap-2 px-4 py-2 rounded-lg transition-colors bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 dark:bg-gray-700 dark:border-gray-600 dark:text-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            <FolderOpen className="w-4 h-4" />
                            Choose Folder
                        </button>
                    </div>

                    <div className="flex items-center justify-between gap-4 p-4 bg-gray-50 dark:bg-gray-700/30 rounded-lg border border-gray-100 dark:border-gray-700">
                        <div>
                            <h3 className="font-medium text-gray-900 dark:text-white">Update index</h3>
                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                Reads new and changed documents and forgets deleted ones. Unchanged files are skipped.
                            </p>
                        </div>
                        {running ? (
                            <button
                                onClick={handleCancel}
                                className="flex shrink-0 items-center gap-2 px-4 py-2 rounded-lg transition-colors bg-white border border-gray-200 hover:bg-gray-50 text-gray-700 dark:bg-gray-700 dark:border-gray-600 dark:text-gray-200"
                            >
                                <XCircle className="w-4 h-4" />
                                Cancel
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
                                Index Now
                            </button>
                        )}
                    </div>

                    {error && (
                        <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>
                    )}

                    {status && <IndexingProgress status={status} />}
                </div>
            </section>

            <section className="flex items-start gap-3 p-4 text-sm text-gray-600 dark:text-gray-300">
                <ShieldCheck className="w-5 h-5 shrink-0 text-green-600 dark:text-green-400" />
                <p>
                    Documents are read and indexed on this computer only. The search engine accepts connections from
                    this application alone and nothing is sent over the network.
                </p>
            </section>
        </div>
    );
};
