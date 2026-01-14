import { AppConfig } from '../types';

const resolveElasticUrl = (): string => {
    // Robust check for Dev mode (Vite typically serves on 5173)
    const isDev = import.meta.env.DEV || (typeof window !== 'undefined' && window.location.port === '5173');

    if (isDev) {
        console.info("[AppConfig] Dev mode detected. Forcing /api proxy to avoid CORS.");
        return "/api";
    }

    const url = import.meta.env.VITE_ELASTIC_URL || import.meta.env.VITE_ELASTICSEARCH_URL;
    if (!url) {
        console.warn("[AppConfig] VITE_ELASTIC_URL is not defined. Defaulting to localhost:9200");
        return "http://localhost:9200";
    }
    return url;
};

const resolveElasticIndex = (): string => {
    return import.meta.env.VITE_ELASTIC_INDEX || 'filesearch';
};

export const config: AppConfig & { app: { title: string, version: string } } = {
    elasticsearch: {
        url: resolveElasticUrl(),
        indexName: resolveElasticIndex(),
    },
    app: {
        title: import.meta.env.VITE_APP_TITLE || 'FileSearch',
        version: '1.0.0',
    },
};

// Singleton initialization logger
let initialized = false;
export const initConfig = () => {
    if (!initialized) {
        console.info(`[AppConfig] Initialized. Target: ${config.elasticsearch.url}, Index: ${config.elasticsearch.indexName}`);
        initialized = true;
    }
};
