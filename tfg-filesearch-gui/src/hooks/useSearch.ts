import { useState, useEffect, useRef } from 'react';
import { search } from '../services/searchApi';
import { SearchFilters, SearchResult } from '../types';

const SEARCH_DEBOUNCE_MS = 300;

export function useSearch() {
    const [query, setQuery] = useState('');
    const [filters, setFilters] = useState<SearchFilters>({ extensions: [] });
    const [results, setResults] = useState<SearchResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const latestRequest = useRef(0);

    useEffect(() => {
        if (!query.trim() && filters.extensions.length === 0) {
            latestRequest.current++;
            setResults(null);
            setError(null);
            setLoading(false);
            return;
        }

        const timer = setTimeout(async () => {
            // Ignore responses that arrive after a newer search was started.
            const requestId = ++latestRequest.current;
            setLoading(true);
            setError(null);
            try {
                const res = await search(query, filters);
                if (requestId === latestRequest.current) setResults(res);
            } catch (err) {
                if (requestId !== latestRequest.current) return;
                setError(err instanceof Error ? err.message : 'No se pudo conectar con el buscador.');
                setResults(null);
            } finally {
                if (requestId === latestRequest.current) setLoading(false);
            }
        }, SEARCH_DEBOUNCE_MS);

        return () => clearTimeout(timer);
    }, [query, filters]);

    return {
        query,
        setQuery,
        filters,
        setFilters,
        results,
        loading,
        error,
    };
}
