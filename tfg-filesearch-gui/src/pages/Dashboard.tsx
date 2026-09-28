import { useState, useEffect, useCallback, useMemo } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { FileText, Database, FolderOpen, Search } from 'lucide-react';
import { AnalyticsGrid } from '../components/dashboard/AnalyticsGrid';
import { IndexChart } from '../components/dashboard/IndexChart';
import { StatCard } from '../components/dashboard/StatCard';
import { ErrorCard } from '../components/ErrorCard';
import { getStats, IndexMissingError, SearchEngineUnavailableError } from '../services/searchApi';
import { IndexStats, SystemAnalytics } from '../types';
import { formatBytes } from '../lib/utils';

type LoadError = { type: 'offline' | 'no-index' | 'error'; message: string };

export function Dashboard() {
  const [stats, setStats] = useState<IndexStats | null>(null);
  const [analytics, setAnalytics] = useState<SystemAnalytics | null>(null);
  const [loadError, setLoadError] = useState<LoadError | null>(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  const loadData = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    const [statsResult, analyticsResult] = await Promise.allSettled([
      getStats(),
      window.electronAPI ? window.electronAPI.getAnalytics() : Promise.resolve(null),
    ]);

    if (statsResult.status === 'fulfilled') {
      setStats(statsResult.value);
    } else {
      const error = statsResult.reason;
      setStats(null);
      setLoadError({
        type: error instanceof IndexMissingError ? 'no-index'
          : error instanceof SearchEngineUnavailableError ? 'offline'
          : 'error',
        message: error instanceof Error ? error.message : 'Could not load index statistics.',
      });
    }
    setAnalytics(analyticsResult.status === 'fulfilled' ? analyticsResult.value : null);
    setLoading(false);
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const chartData = useMemo(
    () => Object.entries(stats?.fileTypes ?? {}).map(([name, value]) => ({ name: name.toUpperCase(), value })),
    [stats],
  );

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <div className="p-8 max-w-7xl mx-auto pb-24 space-y-8">
      <header className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900 dark:text-white tracking-tight">Dashboard Overview</h1>
        <p className="text-gray-500 dark:text-gray-400 mt-2">
          System performance metrics and indexing statistics.
        </p>
      </header>

      {/* Main Analytics Grid */}
      {analytics && <AnalyticsGrid data={analytics} />}

      {loadError && (
        <ErrorCard
          type={loadError.type}
          message={loadError.message}
          onRetry={loadData}
          onAction={loadError.type === 'no-index' ? () => navigate('/settings') : undefined}
          actionLabel="Index a Folder"
        />
      )}

      {/* Quick Stats Cards */}
      {stats && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <StatCard
            title="Indexed Documents"
            value={stats.documentCount.toLocaleString()}
            icon={FileText}
            color="blue"
          />
          <StatCard
            title="Index Size"
            value={formatBytes(stats.sizeInBytes)}
            icon={Database}
            color="purple"
          />
        </div>
      )}

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="lg:col-span-2 bg-white dark:bg-gray-800 p-6 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm"
        >
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-6">File Type Distribution</h3>
          {chartData.length > 0 ? (
            <IndexChart data={chartData} />
          ) : (
            <p className="text-sm text-gray-500 dark:text-gray-400">No documents indexed yet.</p>
          )}
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="bg-white dark:bg-gray-800 p-6 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm"
        >
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Quick Actions</h3>
          <div className="space-y-3">
            <button
              onClick={() => navigate('/search')}
              className="w-full text-left px-4 py-3 rounded-lg bg-gray-50 dark:bg-gray-700/50 hover:bg-blue-50 dark:hover:bg-blue-900/20 text-gray-700 dark:text-gray-300 hover:text-blue-600 dark:hover:text-blue-400 transition-colors flex items-center justify-between"
            >
              <span className="font-medium">Search Documents</span>
              <Search className="w-4 h-4" />
            </button>
            <button
              onClick={() => navigate('/settings')}
              className="w-full text-left px-4 py-3 rounded-lg bg-gray-50 dark:bg-gray-700/50 hover:bg-blue-50 dark:hover:bg-blue-900/20 text-gray-700 dark:text-gray-300 hover:text-blue-600 dark:hover:text-blue-400 transition-colors flex items-center justify-between"
            >
              <span className="font-medium">Index a Folder</span>
              <FolderOpen className="w-4 h-4" />
            </button>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
