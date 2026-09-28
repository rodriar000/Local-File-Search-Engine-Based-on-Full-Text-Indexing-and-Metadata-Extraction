import React from 'react';
import { ResultCard } from './ResultCard';
import { SearchResult } from '../../types';
import { EmptyState } from '../EmptyState';
import { AnimatePresence } from 'framer-motion';

interface ResultsListProps {
    results: SearchResult | null;
    loading: boolean;
    onResultClick: (hit: SearchResult['hits'][0]) => void;
}

export const ResultsList: React.FC<ResultsListProps> = ({ results, loading, onResultClick }) => {
    if (loading) {
        return (
            <div className="space-y-4">
                {[...Array(3)].map((_, i) => (
                    <div key={i} className="bg-white dark:bg-gray-800 rounded-xl p-6 border border-gray-100 dark:border-gray-700 animate-pulse">
                        <div className="flex gap-4">
                            <div className="w-12 h-12 bg-gray-200 dark:bg-gray-700 rounded-lg" />
                            <div className="flex-1 space-y-3">
                                <div className="h-5 bg-gray-200 dark:bg-gray-700 rounded w-1/3" />
                                <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded w-1/4" />
                                <div className="h-16 bg-gray-200 dark:bg-gray-700 rounded w-full" />
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        );
    }

    if (!results) {
        return <EmptyState type="welcome" />;
    }

    if (results.hits.length === 0) {
        return <EmptyState type="no-results" />;
    }

    return (
        <div className="space-y-4 pb-10">
            <div className="flex items-center justify-between text-sm text-gray-500 dark:text-gray-400 px-1">
                <span>
                    {results.totalHits.toLocaleString('es-ES')} {results.totalHits === 1 ? 'documento encontrado' : 'documentos encontrados'}
                    <span className="ml-1 text-xs opacity-75">
                        ({results.metrics?.execution.totalTimeMs.toFixed(0) || results.took} ms)
                    </span>
                </span>
                {results.totalHits > results.hits.length && <span>Se muestran los {results.hits.length} más relevantes</span>}
            </div>

            <AnimatePresence mode="popLayout">
                {results.hits.map((hit) => (
                    <ResultCard
                        key={hit.document.path}
                        hit={hit}
                        onClick={() => onResultClick(hit)}
                    />
                ))}
            </AnimatePresence>
        </div>
    );
};
