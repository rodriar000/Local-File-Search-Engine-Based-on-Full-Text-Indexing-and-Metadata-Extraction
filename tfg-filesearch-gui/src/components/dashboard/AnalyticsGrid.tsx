import { motion } from 'framer-motion';
import { SystemAnalytics } from '../../types';
import { Timer, Search, Calendar } from 'lucide-react';

interface Props {
    data: SystemAnalytics;
}

export function AnalyticsGrid({ data }: Props) {

    const formatDate = (iso: string) => {
        if (!iso) return 'Nunca';
        return new Date(iso).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' });
    };

    const avgSearchTime = data.search && data.search.totalSearches > 0
        ? (data.search.totalSearchTimeMs / data.search.totalSearches).toLocaleString('es-ES', { maximumFractionDigits: 1 })
        : '0';

    const items = [
        {
            label: 'Búsquedas realizadas',
            value: data.search ? data.search.totalSearches.toLocaleString('es-ES') : '0',
            icon: Search,
            color: 'text-purple-500',
            bg: 'bg-purple-500/10'
        },
        {
            label: 'Tiempo medio de búsqueda',
            value: `${avgSearchTime} ms`,
            icon: Timer,
            color: 'text-green-500',
            bg: 'bg-green-500/10'
        },
        {
            label: 'Última actualización del índice',
            value: data.indexing ? formatDate(data.indexing.lastRun) : 'Nunca',
            icon: Calendar,
            color: 'text-gray-500',
            bg: 'bg-gray-500/10'
        }
    ];

    return (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            {items.map((item, idx) => (
                <motion.div
                    key={item.label}
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: idx * 0.1 }}
                    className="bg-white dark:bg-gray-800 p-4 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm"
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
