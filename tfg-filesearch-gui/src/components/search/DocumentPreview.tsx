import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ChevronDown, ChevronUp, ExternalLink, FolderOpen, X } from 'lucide-react';
import { getPreview } from '../../services/searchApi';
import { DocumentPreviewData } from '../../types';
import { formatBytes } from '../../lib/utils';
import { HighlightedText } from './HighlightedText';

interface DocumentPreviewProps {
    path: string;
    query: string;
    onClose: () => void;
    onOpen: (path: string) => void;
}

/**
 * Side panel with the document's text and every match highlighted, so a
 * result can be checked without opening the file.
 */
export const DocumentPreview: React.FC<DocumentPreviewProps> = ({ path, query, onClose, onOpen }) => {
    const [preview, setPreview] = useState<DocumentPreviewData | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [matchCount, setMatchCount] = useState(0);
    const [current, setCurrent] = useState(0);
    const textRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        let cancelled = false;
        setPreview(null);
        setError(null);
        getPreview(path, query)
            .then((data) => { if (!cancelled) setPreview(data); })
            .catch((err) => { if (!cancelled) setError(err instanceof Error ? err.message : String(err)); });
        return () => { cancelled = true; };
    }, [path, query]);

    const marks = () => Array.from(textRef.current?.querySelectorAll('mark') ?? []);

    const goTo = useCallback((index: number) => {
        const all = marks();
        if (all.length === 0) return;
        const next = (index + all.length) % all.length;
        all.forEach((mark, i) => mark.classList.toggle('ring-2', i === next));
        all[next].scrollIntoView({ block: 'center' });
        setCurrent(next);
    }, []);

    // Jump to the first match once the text is shown.
    useEffect(() => {
        if (!preview) return;
        setMatchCount(marks().length);
        goTo(0);
    }, [preview, goTo]);

    useEffect(() => {
        const onKey = (event: KeyboardEvent) => {
            if (event.key === 'Escape') onClose();
            const typing = event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement;
            if (event.key === 'Enter' && !typing && matchCount > 0) goTo(event.shiftKey ? current - 1 : current + 1);
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onClose, goTo, current, matchCount]);

    const filename = preview?.filename ?? path.split(/[\\/]/).pop();

    return (
        <aside
            role="dialog"
            aria-label={`Preview of ${filename}`}
            className="fixed inset-y-0 right-0 z-40 w-full max-w-3xl flex flex-col bg-white dark:bg-gray-900 border-l border-gray-200 dark:border-gray-700 shadow-2xl"
        >
            <header className="flex items-start gap-3 p-4 border-b border-gray-100 dark:border-gray-800">
                <div className="min-w-0 flex-1">
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white truncate" title={filename}>{filename}</h2>
                    <p className="text-xs text-gray-500 dark:text-gray-400 truncate font-mono" title={path}>{path}</p>
                    {preview && (
                        <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                            {formatBytes(preview.size)}
                            {preview.modifiedAt && ` · ${new Date(preview.modifiedAt).toLocaleDateString()}`}
                            {preview.author && ` · ${preview.author}`}
                        </p>
                    )}
                </div>
                <button onClick={() => onOpen(path)} className="flex items-center gap-2 px-3 py-2 rounded-lg bg-blue-600 text-white hover:bg-blue-700 text-sm">
                    <ExternalLink className="w-4 h-4" /> Open
                </button>
                <button
                    onClick={() => window.electronAPI?.showInFolder(path)}
                    title="Show in folder"
                    aria-label="Show in folder"
                    className="p-2 rounded-lg border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800"
                >
                    <FolderOpen className="w-4 h-4" />
                </button>
                <button onClick={onClose} aria-label="Close preview" className="p-2 rounded-lg text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800">
                    <X className="w-5 h-5" />
                </button>
            </header>

            {matchCount > 0 && (
                <div className="flex items-center justify-between px-4 py-2 text-sm text-gray-600 dark:text-gray-300 border-b border-gray-100 dark:border-gray-800">
                    <span>Match {current + 1} of {matchCount}</span>
                    <div className="flex gap-1">
                        <button onClick={() => goTo(current - 1)} aria-label="Previous match" className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-800">
                            <ChevronUp className="w-4 h-4" />
                        </button>
                        <button onClick={() => goTo(current + 1)} aria-label="Next match" className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-800">
                            <ChevronDown className="w-4 h-4" />
                        </button>
                    </div>
                </div>
            )}

            <div className="flex-1 overflow-y-auto p-6">
                {error && <p role="alert" className="text-sm text-red-600 dark:text-red-400">{error}</p>}
                {!preview && !error && <p className="text-sm text-gray-500">Loading…</p>}
                {preview && preview.text.trim() === '' && (
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                        No text could be read from this document (for example a scan without OCR, or a password-protected file). Open it to see it.
                    </p>
                )}
                {preview && preview.text.trim() !== '' && (
                    <div ref={textRef} className="whitespace-pre-wrap break-words font-serif text-[15px] leading-relaxed text-gray-800 dark:text-gray-200">
                        <HighlightedText fragment={preview.text} />
                    </div>
                )}
                {preview?.truncated && (
                    <p className="mt-6 text-sm text-amber-700 dark:text-amber-400">
                        This is a long document; only its beginning is shown. Open it to read the rest.
                    </p>
                )}
            </div>
        </aside>
    );
};
