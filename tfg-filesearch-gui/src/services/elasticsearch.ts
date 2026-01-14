import axios from 'axios';
import { AppConfig, SearchFilters, SearchResult, SearchMetrics } from '../types';
import { config as appConfig, initConfig } from '../config/appConfig';

// Initialize configuration logger
initConfig();

function normalizeBaseUrl(url: string): string {
    if (!url) return 'http://localhost:9200';
    // Remove trailing slash
    return url.endsWith('/') ? url.slice(0, -1) : url;
}

export class ElasticsearchService {
    private config: AppConfig['elasticsearch'];

    constructor(config?: AppConfig['elasticsearch']) {
        this.config = config || appConfig.elasticsearch;
        this.config.url = normalizeBaseUrl(this.config.url);
    }

    public updateConfig(config: AppConfig['elasticsearch']) {
        if (!config) return;
        const url = config.url ? normalizeBaseUrl(config.url) : this.config.url;
        this.config = { ...config, url };
        console.info(`[ElasticsearchService] Config updated: ${this.config.url} / ${this.config.indexName}`);
    }

    async search(query: string, filters: SearchFilters, from: number = 0, size: number = 20): Promise<SearchResult> {
        // Safe check
        if (!this.config.url) throw new Error("Elasticsearch URL is not configured");

        const startTime = performance.now();
        const must: any[] = [];

        // Text search
        if (query && query.trim().length > 0) {
            must.push({
                multi_match: {
                    query: query,
                    fields: ['filename^2', 'title^1.5', 'content', 'author'],
                    fuzziness: 'AUTO'
                }
            });
        } else {
            must.push({ match_all: {} });
        }

        // Filters
        const filter: any[] = [];

        // Extension filter
        if (filters.extensions.length > 0) {
            filter.push({
                terms: {
                    'extension': filters.extensions
                }
            });
        }

        // Size filter
        if (filters.sizeMin !== undefined || filters.sizeMax !== undefined) {
            const range: any = {};
            if (filters.sizeMin !== undefined) range.gte = filters.sizeMin * 1024 * 1024; // MB to bytes
            if (filters.sizeMax !== undefined) range.lte = filters.sizeMax * 1024 * 1024;
            filter.push({ range: { size: range } });
        }

        // Date filter
        if (filters.dateFrom || filters.dateTo) {
            const range: any = {};
            if (filters.dateFrom) range.gte = filters.dateFrom;
            if (filters.dateTo) range.lte = filters.dateTo;
            filter.push({ range: { modified_at: range } });
        }

        const body = {
            from,
            size,
            query: {
                bool: {
                    must,
                    filter
                }
            },
            highlight: {
                fields: {
                    content: { fragment_size: 150, number_of_fragments: 1 }
                },
                pre_tags: ['<mark class="bg-yellow-200 text-black rounded px-0.5">'],
                post_tags: ['</mark>']
            },
            _source: {
                excludes: ['content'] // Don't return full content to save bandwidth
            }
        };

        try {
            const response = await axios.post(`${this.config.url}/${this.config.indexName}/_search`, body);
            const endTime = performance.now();
            const hits = response.data.hits;
            const totalTimeMs = endTime - startTime;

            const metrics: SearchMetrics = {
                params: { query, filters },
                execution: {
                    totalTimeMs,
                    elasticTookMs: response.data.took,
                    timestamp: new Date().toISOString()
                }
            };

            return {
                totalHits: hits.total.value,
                took: response.data.took,
                metrics,
                hits: hits.hits.map((hit: any) => ({
                    score: hit._score,
                    document: hit._source,
                    highlight: hit.highlight
                }))
            };
        } catch (error) {
            // Check if error is due to network/config
            if (axios.isAxiosError(error) && !error.response) {
                console.error('[ElasticsearchService] Network Error or Invalid URL:', this.config.url);
            }
            console.error('Search failed:', error);
            throw error;
        }
    }

    async getStats(): Promise<any> {
        if (!this.config.url) return { totalDocs: 0, sizeInBytes: 0, fileTypes: {} };

        try {
            // Parallel requests for basic stats and aggregations
            const [statsResponse, searchResponse] = await Promise.all([
                axios.get(`${this.config.url}/${this.config.indexName}/_stats`),
                axios.post(`${this.config.url}/${this.config.indexName}/_search`, {
                    size: 0,
                    aggs: {
                        extensions: {
                            terms: { field: "extension", size: 10 }
                        }
                    }
                })
            ]);

            const totalDocs = statsResponse.data._all.primaries.docs.count;
            const sizeInBytes = statsResponse.data._all.primaries.store.size_in_bytes;

            const fileTypes: Record<string, number> = {};
            searchResponse.data.aggregations.extensions.buckets.forEach((bucket: any) => {
                fileTypes[bucket.key] = bucket.doc_count;
            });

            return {
                documentCount: totalDocs, /* mapped to match Dashboard prop expectation */
                sizeInBytes,
                fileTypes
            };
        } catch (error) {
            console.error('Stats failed:', error);
            // Don't throw for stats, return empty safe object to prevent dashboard crash
            return {
                documentCount: 0,
                sizeInBytes: 0,
                fileTypes: {}
            };
        }
    }
}

export const elasticsearchService = new ElasticsearchService();
