import React from 'react';
import { AlertCircle, WifiOff, Database, RefreshCw } from 'lucide-react';
import { Button } from './ui/button';
import { Card } from './ui/card';

interface ErrorCardProps {
    type: 'offline' | 'no-index' | 'error';
    message: string;
    onRetry?: () => void;
    onAction?: () => void;
    actionLabel?: string;
}

export const ErrorCard: React.FC<ErrorCardProps> = ({
    type,
    message,
    onRetry,
    onAction,
    actionLabel = 'Take Action'
}) => {
    const getIcon = () => {
        switch (type) {
            case 'offline':
                return <WifiOff className="w-16 h-16 mb-4 text-red-500 dark:text-red-400" />;
            case 'no-index':
                return <Database className="w-16 h-16 mb-4 text-orange-500 dark:text-orange-400" />;
            default:
                return <AlertCircle className="w-16 h-16 mb-4 text-yellow-500 dark:text-yellow-400" />;
        }
    };

    const getTitle = () => {
        switch (type) {
            case 'offline':
                return 'Elasticsearch is Offline';
            case 'no-index':
                return 'Index Not Found';
            default:
                return 'Something Went Wrong';
        }
    };

    const getDescription = () => {
        switch (type) {
            case 'offline':
                return "Can't reach Elasticsearch. Make sure it's running on localhost:9200";
            case 'no-index':
                return "The filesearch index doesn't exist yet. Create it to start searching.";
            default:
                return message;
        }
    };

    const getHelpText = () => {
        switch (type) {
            case 'offline':
                return 'Try: brew services start elasticsearch-full';
            case 'no-index':
                return 'Run: java -jar filesearch.jar create-index';
            default:
                return '';
        }
    };

    return (
        <div className="flex items-center justify-center min-h-[60vh]">
            <Card className="max-w-md p-8 text-center">
                <div className="flex flex-col items-center">
                    {getIcon()}

                    <h2 className="text-2xl font-bold mb-2 text-gray-900 dark:text-white">
                        {getTitle()}
                    </h2>

                    <p className="text-gray-600 dark:text-gray-300 mb-4">
                        {getDescription()}
                    </p>

                    {getHelpText() && (
                        <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3 mb-6 w-full">
                            <p className="text-sm text-blue-800 dark:text-blue-200 font-mono">
                                {getHelpText()}
                            </p>
                        </div>
                    )}

                    <div className="flex gap-3">
                        {onRetry && (
                            <Button onClick={onRetry} variant="outline">
                                <RefreshCw className="w-4 h-4 mr-2" />
                                Retry Connection
                            </Button>
                        )}

                        {onAction && (
                            <Button onClick={onAction}>
                                {actionLabel}
                            </Button>
                        )}
                    </div>
                </div>
            </Card>
        </div>
    );
};
