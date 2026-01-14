import { motion } from 'framer-motion';
import { SystemAnalytics } from '../../types';
import { Timer, Search, Zap, Calendar } from 'lucide-react';

interface Props {
    data: SystemAnalytics;
}

export function AnalyticsGrid({ data }: Props) {

    const formatDate = (iso: string) => {
        if (!iso) return 'Never';
        return new Date(iso).toLocaleDateString() + ' ' + new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    };

    const avgSearchTime = data.search && data.search.totalSearches > 0
        ? (data.search.totalSearchTimeMs / data.search.totalSearches).toFixed(1)
        : '0.0';

    const items = [
        {
            label: 'Indexing Speed',
            value: data.indexing ? `${data.indexing.docsPerSecond.toFixed(0)} doc/s` : 'N/A',
            icon: Zap,
            color: 'text-yellow-500',
            bg: 'bg-yellow-500/10'
        },
        {
            label: 'Avg Index Time',
            value: data.indexing && data.indexing.totalDocuments > 0
                ? `${(data.indexing.durationMs / data.indexing.totalDocuments).toFixed(0)} ms/doc`
                : 'N/A',
            icon: Timer,
            color: 'text-blue-500',
            bg: 'bg-blue-500/10'
        },
        {
            label: 'Total Searches',
            value: data.search ? data.search.totalSearches.toLocaleString() : '0',
            icon: Search,
            color: 'text-purple-500',
            bg: 'bg-purple-500/10'
        },
        {
            label: 'Avg Search Time',
            value: `${avgSearchTime} ms`,
            icon: Timer,
            color: 'text-green-500',
            bg: 'bg-green-500/10'
        },
        {
            label: 'Last Indexing',
            value: data.indexing ? formatDate(data.indexing.lastRun) : 'Never',
            icon: Calendar,
            color: 'text-gray-500',
            bg: 'bg-gray-500/10',
            colSpan: 2
        }
    ];

    return (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
            {items.map((item, idx) => (
                <motion.div
                    key={item.label}
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: idx * 0.1 }}
                    className={`bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm ${item.colSpan ? `col-span-${item.colSpan}` : ''}`}
                >
                    <div className="flex items-center gap-3 mb-2">
                        <div className={`p-2 rounded-lg ${item.bg}`}>
                            <item.icon className={`w-4 h-4 ${item.color}`} />
                        </div>
                        <span className="text-sm font-medium text-gray-500 dark:text-gray-400">
                            {item.label}
                        </span>
                    </div>
                    <p className="text-2xl font-bold text-gray-900 dark:text-white pl-11">
                        {item.value}
                    </p>
                </motion.div>
            ))}
        </div>
    );
}
