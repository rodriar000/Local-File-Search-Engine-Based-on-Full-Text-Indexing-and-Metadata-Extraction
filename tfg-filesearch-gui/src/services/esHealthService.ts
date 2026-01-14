import axios from 'axios';
import { AppConfig } from '../types';

export type HealthStatus = 'connected' | 'index-missing' | 'offline' | 'checking';

export class EsHealthService {
    private config: AppConfig;

    constructor(config: AppConfig) {
        this.config = config;
    }

    async checkHealth(): Promise<HealthStatus> {
        try {
            // 1. Check basic connectivity
            await axios.get(this.config.elasticsearch.url, { timeout: 2000 });

            // 2. Check if index exists
            try {
                await axios.head(`${this.config.elasticsearch.url}/${this.config.elasticsearch.indexName}`, { timeout: 2000 });
                return 'connected';
            } catch (err: any) {
                if (err.response && err.response.status === 404) {
                    return 'index-missing';
                }
                throw err;
            }
        } catch (error) {
            console.error('Health check failed:', error);
            return 'offline';
        }
    }
}
