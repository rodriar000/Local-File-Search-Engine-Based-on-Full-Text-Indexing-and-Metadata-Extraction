import React from 'react';
import { Search, X, Loader2 } from 'lucide-react';
import { cn } from '../../lib/utils';

interface SearchInputProps {
    value: string;
    onChange: (value: string) => void;
    onClear: () => void;
    loading?: boolean;
    placeholder?: string;
    className?: string;
}

export const SearchInput: React.FC<SearchInputProps> = ({
    value,
    onChange,
    onClear,
    loading,
    placeholder = "Buscar en los documentos…",
    className
}) => {
    return (
        <div className={cn("relative group", className)}>
            <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
                <Search className="h-5 w-5 text-gray-400 group-focus-within:text-blue-500 transition-colors" />
            </div>

            <input
                type="text"
                value={value}
                onChange={(e) => onChange(e.target.value)}
                className="block w-full pl-11 pr-10 py-3 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-xl text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all shadow-sm"
                placeholder={placeholder}
                autoFocus
            />

            <div className="absolute inset-y-0 right-0 pr-3 flex items-center">
                {loading ? (
                    <Loader2 className="h-5 w-5 text-blue-500 animate-spin" />
                ) : value ? (
                    <button
                        onClick={onClear}
                        aria-label="Borrar búsqueda"
                        className="p-1 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-full text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
                    >
                        <X className="h-4 w-4" />
                    </button>
                ) : (
                    <div className="hidden group-focus-within:flex items-center gap-1 px-2 py-1 bg-gray-100 dark:bg-gray-700 rounded text-xs text-gray-500 font-medium">
                        <span className="text-xs">ESC</span>
                    </div>
                )}
            </div>
        </div>
    );
};
