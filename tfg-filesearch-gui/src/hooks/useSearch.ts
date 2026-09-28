import { useState, useEffect, useMemo, useRef } from 'react';
import { ElasticsearchService } from '../services/elasticsearch';
import { useAppStore } from '../store/useAppStore';
import { SearchFilters, SearchResult } from '../types';

const SEARCH_DEBOUNCE_MS = 300;

export function useSearch() {
    const config = useAppStore((state) => state.config);
    const [query, setQuery] = useState('');
    const [filters, setFilters] = useState<SearchFilters>({ extensions: [] });
    const [results, setResults] = useState<SearchResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const latestRequest = useRef(0);

    const searchService = useMemo(
        () => new ElasticsearchService(config.elasticsearch),
        [config.elasticsearch.url, config.elasticsearch.indexName],
    );

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
                const res = await searchService.search(query, filters);
                if (requestId === latestRequest.current) setResults(res);
            } catch (err) {
                if (requestId !== latestRequest.current) return;
                setError(err instanceof Error ? err.message : 'Could not connect to the search engine.');
                setResults(null);
            } finally {
                if (requestId === latestRequest.current) setLoading(false);
            }
        }, SEARCH_DEBOUNCE_MS);

        return () => clearTimeout(timer);
    }, [query, filters, searchService]);

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
