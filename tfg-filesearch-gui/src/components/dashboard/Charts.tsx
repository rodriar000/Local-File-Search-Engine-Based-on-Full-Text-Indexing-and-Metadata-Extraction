import { ResponsiveContainer, BarChart as RechartsBarChart, Bar, LineChart as RechartsLineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Cell } from 'recharts';
import { motion } from 'framer-motion';

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899'];

interface BaseChartProps {
    title: string;
}

interface BarChartProps extends BaseChartProps {
    data: { name: string; value: number }[];
}

interface LineChartProps extends BaseChartProps {
    data: { name: string; value: number }[];
    lineColor?: string;
    unit?: string;
}

const ChartContainer = ({ title, children }: { title: string, children: React.ReactNode }) => (
    <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className="bg-white dark:bg-gray-800 p-6 rounded-xl border border-gray-100 dark:border-gray-700 shadow-sm"
    >
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-6">{title}</h3>
        <div className="h-[250px] min-h-[250px] w-full">
            {children}
        </div>
    </motion.div>
);

const CustomTooltip = ({ active, payload, label, unit }: any) => {
    if (active && payload && payload.length) {
        return (
            <div className="bg-white dark:bg-gray-900 p-3 border border-gray-100 dark:border-gray-700 shadow-lg rounded-lg">
                <p className="text-sm font-medium text-gray-600 dark:text-gray-400">{label}</p>
                <p className="text-lg font-bold text-gray-900 dark:text-white">
                    {Number(payload[0].value).toFixed(2)} {unit}
                </p>
            </div>
        );
    }
    return null;
};

export function FileTypeBarChart({ title, data }: BarChartProps) {
    return (
        <ChartContainer title={title}>
            <ResponsiveContainer width="100%" height="100%">
                <RechartsBarChart data={data}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" opacity={0.3} />
                    <XAxis
                        dataKey="name"
                        axisLine={false}
                        tickLine={false}
                        tick={{ fill: '#9ca3af', fontSize: 12 }}
                        dy={10}
                    />
                    <YAxis
                        axisLine={false}
                        tickLine={false}
                        tick={{ fill: '#9ca3af', fontSize: 12 }}
                    />
                    <Tooltip cursor={{ fill: 'transparent' }} content={<CustomTooltip unit="docs" />} />
                    <Bar dataKey="value" radius={[4, 4, 0, 0]} barSize={40}>
                        {data.map((_, index) => (
                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                        ))}
                    </Bar>
                </RechartsBarChart>
            </ResponsiveContainer>
        </ChartContainer>
    );
}

export function TimeLineChart({ title, data, lineColor = '#3b82f6', unit = 'ms' }: LineChartProps) {
    return (
        <ChartContainer title={title}>
            <ResponsiveContainer width="100%" height="100%">
                <RechartsLineChart data={data}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e5e7eb" opacity={0.3} />
                    <XAxis
                        dataKey="name"
                        axisLine={false}
                        tickLine={false}
                        tick={{ fill: '#9ca3af', fontSize: 12 }}
                        dy={10}
                        minTickGap={30}
                    />
                    <YAxis
                        axisLine={false}
                        tickLine={false}
                        tick={{ fill: '#9ca3af', fontSize: 12 }}
                    />
                    <Tooltip cursor={{ stroke: lineColor, strokeWidth: 1, strokeDasharray: '3 3' }} content={<CustomTooltip unit={unit} />} />
                    <Line
                        type="monotone"
                        dataKey="value"
                        stroke={lineColor}
                        strokeWidth={2}
                        dot={{ r: 3, fill: lineColor, strokeWidth: 0 }}
                        activeDot={{ r: 6, strokeWidth: 0 }}
                    />
                </RechartsLineChart>
            </ResponsiveContainer>
        </ChartContainer>
    );
}
