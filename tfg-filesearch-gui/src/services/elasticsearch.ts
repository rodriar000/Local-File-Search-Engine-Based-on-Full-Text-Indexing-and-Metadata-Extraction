import { AppConfig, IndexStats, SearchFilters, SearchResult, SearchMetrics } from '../types';
import { HIGHLIGHT_POST, HIGHLIGHT_PRE } from '../shared/highlight';
import { esRequest } from './esTransport';

type EsTarget = AppConfig['elasticsearch'];

export class IndexMissingError extends Error {
    constructor(indexName: string) {
        super(`The index "${indexName}" does not exist yet. Index a folder from Settings first.`);
        this.name = 'IndexMissingError';
    }
}

function assertOk(status: number, target: EsTarget, operation: string) {
    if (status === 404) throw new IndexMissingError(target.indexName);
    if (status < 200 || status >= 300) {
        throw new Error(`${operation} failed (search engine returned HTTP ${status}).`);
    }
}

export class ElasticsearchService {
    constructor(private readonly target: EsTarget) {}

    async search(query: string, filters: SearchFilters, from: number = 0, size: number = 20): Promise<SearchResult> {
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
                // Marker characters instead of HTML tags: fragments are rendered as text.
                pre_tags: [HIGHLIGHT_PRE],
                post_tags: [HIGHLIGHT_POST]
            },
            _source: {
                excludes: ['content'] // Don't return full content to save bandwidth
            }
        };

        const response = await esRequest(this.target, 'POST', `/${this.target.indexName}/_search`, body);
        assertOk(response.status, this.target, 'Search');

        const totalTimeMs = performance.now() - startTime;
        const hits = response.data.hits;

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
    }

    async getStats(): Promise<IndexStats> {
        const index = this.target.indexName;
        const [statsResponse, aggsResponse] = await Promise.all([
            esRequest(this.target, 'GET', `/${index}/_stats`),
            esRequest(this.target, 'POST', `/${index}/_search`, {
                size: 0,
                aggs: {
                    extensions: {
                        terms: { field: 'extension', size: 10 }
                    }
                }
            })
        ]);
        assertOk(statsResponse.status, this.target, 'Loading index statistics');
        assertOk(aggsResponse.status, this.target, 'Loading index statistics');

        const fileTypes: Record<string, number> = {};
        aggsResponse.data.aggregations.extensions.buckets.forEach((bucket: any) => {
            fileTypes[bucket.key] = bucket.doc_count;
        });

        return {
            documentCount: statsResponse.data._all.primaries.docs.count,
            sizeInBytes: statsResponse.data._all.primaries.store.size_in_bytes,
            fileTypes
        };
    }
}
