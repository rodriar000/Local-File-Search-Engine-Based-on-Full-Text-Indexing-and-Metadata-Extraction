import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { FileText, Database, HardDrive, Activity } from 'lucide-react';
import { AnalyticsGrid } from '../components/dashboard/AnalyticsGrid';
import { IndexChart } from '../components/dashboard/IndexChart';
import { StatCard } from '../components/dashboard/StatCard';
import { elasticsearchService } from '../services/elasticsearch';
import { useAppStore } from '../store/useAppStore';
import { IndexStats, SystemAnalytics } from '../types';
import { formatBytes } from '../lib/utils';

export function Dashboard() {
  const [stats, setStats] = useState<IndexStats | null>(null);
  const [analytics, setAnalytics] = useState<SystemAnalytics | null>(null);
  const [loading, setLoading] = useState(true);
  const { config } = useAppStore();

  useEffect(() => {
    async function loadData() {
      try {
        // Initial fetch
        const [esStats, sysAnalytics] = await Promise.all([
          elasticsearchService.getStats(),
          window.ipcRenderer ? window.ipcRenderer.invoke('get-analytics') : Promise.resolve({
            indexing: {
              totalDocuments: 15420,
              sizeBytes: 450 * 1024 * 1024,
              lastRun: new Date().toISOString(),
              durationMs: 45000,
              docsPerSecond: 342
            },
            search: {
              totalSearches: 1250,
              totalSearchTimeMs: 45000,
              avgLatencyMs: 36,
              history: []
            }
          })
        ]);
        setStats(esStats);
        setAnalytics(sysAnalytics);
      } catch (error) {
        console.error('Failed to load dashboard data:', error);
      } finally {
        setLoading(false);
      }
    }

    if (config.elasticsearch?.url) {
      loadData();
    } else {
      // Fallback for dev/demo if no URL configured or immediate load
      loadData();
    }
  }, [config]);

  // Mock chart data if real data isn't available yet or strictly for visualization
  // In a real scenario, this should come from 'analytics'
  const chartData = [
    { name: 'PDF', value: 45 },
    { name: 'DOCX', value: 25 },
    { name: 'TXT', value: 15 },
    { name: 'MD', value: 10 },
    { name: 'OTHER', value: 5 },
  ];

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

      {/* Quick Stats Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          title="Total Documents"
          value={stats?.documentCount.toLocaleString() || (analytics?.indexing?.totalDocuments.toLocaleString() || '0')}
          icon={FileText}
          color="blue"
          trend="+12%"
          trendUp={true}
        />
        <StatCard
          title="Index Size"
          value={formatBytes(stats?.sizeInBytes || analytics?.indexing?.sizeBytes || 0)}
          icon={Database}
          color="purple"
        />
        <StatCard
          title="Storage Used"
          value={formatBytes(stats?.sizeInBytes || analytics?.indexing?.sizeBytes || 0)}
          icon={HardDrive}
          color="orange"
        />
        <StatCard
          title="Cluster Health"
          value={stats?.health || "Green"}
          icon={Activity}
          color="green"
        />
      </div>

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="lg:col-span-2 bg-white dark:bg-gray-800 p-6 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm"
        >
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-6">File Type Distribution</h3>
          <IndexChart data={chartData} />
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="bg-white dark:bg-gray-800 p-6 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm"
        >
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Quick Actions</h3>
          <div className="space-y-3">
            <button className="w-full text-left px-4 py-3 rounded-lg bg-gray-50 dark:bg-gray-700/50 hover:bg-blue-50 dark:hover:bg-blue-900/20 text-gray-700 dark:text-gray-300 hover:text-blue-600 dark:hover:text-blue-400 transition-colors flex items-center justify-between group">
              <span className="font-medium">Reindex All Documents</span>
              <Activity className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity" />
            </button>
            <button className="w-full text-left px-4 py-3 rounded-lg bg-gray-50 dark:bg-gray-700/50 hover:bg-blue-50 dark:hover:bg-blue-900/20 text-gray-700 dark:text-gray-300 hover:text-blue-600 dark:hover:text-blue-400 transition-colors flex items-center justify-between group">
              <span className="font-medium">Clear Cache</span>
              <Activity className="w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity" />
            </button>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
