import { useState, useEffect, useCallback } from 'react';
import { ElasticsearchService } from '../services/elasticsearch';
import { AppConfig, SearchFilters, SearchResult } from '../types';

const DEFAULT_CONFIG: AppConfig = {
    elasticsearch: {
        url: 'http://localhost:9200',
        indexName: 'filesearch'
    }
};

export function useSearch() {
    const [query, setQuery] = useState('');
    const [filters, setFilters] = useState<SearchFilters>({ extensions: [] });
    const [results, setResults] = useState<SearchResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [config, setConfig] = useState<AppConfig>(() => {
        try {
            const saved = localStorage.getItem('appConfig');
            return saved ? JSON.parse(saved) : DEFAULT_CONFIG;
        } catch {
            return DEFAULT_CONFIG;
        }
    });

    // Persist config
    useEffect(() => {
        localStorage.setItem('appConfig', JSON.stringify(config));
    }, [config]);

    const searchService = new ElasticsearchService(config.elasticsearch);

    const performSearch = useCallback(async (searchQuery: string, searchFilters: SearchFilters) => {
        if (!searchQuery.trim() && searchFilters.extensions.length === 0) {
            setResults(null);
            return;
        }

        setLoading(true);
        setError(null);
        try {
            const res = await searchService.search(searchQuery, searchFilters);
            setResults(res);
        } catch (err) {
            console.error(err);
            setError('Could not connect to search engine.');
            setResults(null);
        } finally {
            setLoading(false);
        }
    }, [config]); // Re-create when config changes

    // Debounce search
    useEffect(() => {
        const timer = setTimeout(() => {
            performSearch(query, filters);
        }, 300);
        return () => clearTimeout(timer);
    }, [query, filters, performSearch]);

    return {
        query,
        setQuery,
        filters,
        setFilters,
        results,
        loading,
        error,
        config,
        setConfig
    };
}
