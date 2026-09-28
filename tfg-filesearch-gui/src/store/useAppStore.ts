import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { AppConfig } from '../types';

interface AppState {
    isSidebarOpen: boolean;
    toggleSidebar: () => void;
    config: AppConfig;
    setConfig: (config: AppConfig) => void;
    /** Folder last chosen for indexing; null until the user picks one. */
    indexFolder: string | null;
    setIndexFolder: (folder: string | null) => void;
    darkMode: boolean;
    toggleDarkMode: () => void;
}

export const useAppStore = create<AppState>()(
    persist(
        (set) => ({
            isSidebarOpen: true,
            toggleSidebar: () => set((state) => ({ isSidebarOpen: !state.isSidebarOpen })),
            config: {
                elasticsearch: {
                    url: 'http://localhost:9200',
                    indexName: 'filesearch',
                }
            },
            setConfig: (config) => set({ config }),
            indexFolder: null,
            setIndexFolder: (indexFolder) => set({ indexFolder }),
            darkMode: false,
            toggleDarkMode: () => set((state) => {
                const newMode = !state.darkMode;
                if (newMode) {
                    document.documentElement.classList.add('dark');
                } else {
                    document.documentElement.classList.remove('dark');
                }
                return { darkMode: newMode };
            }),
        }),
        {
            name: 'app-storage',
            version: 1,
            migrate: (persistedState: any, version: number) => {
                if (version === 0) {
                    // Migration from flat config to nested
                    return {
                        ...persistedState,
                        config: {
                            elasticsearch: {
                                url: persistedState.config?.esUrl || 'http://localhost:9200',
                                indexName: persistedState.config?.indexName || 'filesearch',
                            }
                        }
                    };
                }
                return persistedState as AppState;
            },
        }
    )
);
