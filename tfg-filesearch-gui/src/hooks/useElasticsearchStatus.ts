import { useState, useEffect, useCallback } from 'react';
import axios from 'axios';

export type ElasticsearchStatus = 'checking' | 'online' | 'offline' | 'no-index' | 'error';

interface UseElasticsearchStatusResult {
    status: ElasticsearchStatus;
    message: string;
    retry: () => void;
    isLoading: boolean;
}

const ES_URL = 'http://localhost:9200';
const INDEX_NAME = 'filesearch';

export const useElasticsearchStatus = (): UseElasticsearchStatusResult => {
    const [status, setStatus] = useState<ElasticsearchStatus>('checking');
    const [message, setMessage] = useState<string>('Checking Elasticsearch connection...');
    const [isLoading, setIsLoading] = useState(true);

    const checkStatus = useCallback(async () => {
        setIsLoading(true);
        setStatus('checking');
        setMessage('Checking Elasticsearch connection...');

        try {
            // Step 1: Check if Elasticsearch is reachable
            const healthResponse = await axios.get(ES_URL, { timeout: 3000 });

            if (!healthResponse.data) {
                setStatus('offline');
                setMessage('Cannot reach Elasticsearch');
                setIsLoading(false);
                return;
            }

            // Step 2: Check if index exists
            try {
                const indexResponse = await axios.head(`${ES_URL}/${INDEX_NAME}`, { timeout: 3000 });

                if (indexResponse.status === 200) {
                    setStatus('online');
                    setMessage('Connected to Elasticsearch');
                }
            } catch (indexError: any) {
                if (indexError.response?.status === 404) {
                    setStatus('no-index');
                    setMessage(`Index "${INDEX_NAME}" does not exist`);
                } else {
                    setStatus('error');
                    setMessage('Error checking index');
                }
            }
        } catch (error: any) {
            if (error.code === 'ECONNREFUSED' || error.code === 'ECONNABORTED') {
                setStatus('offline');
                setMessage('Elasticsearch is not running');
            } else {
                setStatus('error');
                setMessage(error.message || 'Unknown error');
            }
        } finally {
            setIsLoading(false);
        }
    }, []);

    const retry = useCallback(() => {
        checkStatus();
    }, [checkStatus]);

    // Auto-check on mount
    useEffect(() => {
        checkStatus();
    }, [checkStatus]);

    return { status, message, retry, isLoading };
};
