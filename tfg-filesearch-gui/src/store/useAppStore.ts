import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AppState {
    isSidebarOpen: boolean;
    toggleSidebar: () => void;
    /** Folder last chosen for indexing; null until the user picks one. */
    indexFolder: string | null;
    setIndexFolder: (folder: string | null) => void;
    darkMode: boolean;
    toggleDarkMode: () => void;
    /** Mark DNI, IBAN, health data… in the preview. */
    showPersonalData: boolean;
    setShowPersonalData: (show: boolean) => void;
}

export const useAppStore = create<AppState>()(
    persist(
        (set) => ({
            isSidebarOpen: true,
            toggleSidebar: () => set((state) => ({ isSidebarOpen: !state.isSidebarOpen })),
            indexFolder: null,
            setIndexFolder: (indexFolder) => set({ indexFolder }),
            showPersonalData: true,
            setShowPersonalData: (showPersonalData) => set({ showPersonalData }),
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
            version: 2,
            // Versions 0 and 1 stored the Elasticsearch address, which no longer exists.
            migrate: (persistedState: any) => {
                const { config: _removed, ...rest } = (persistedState ?? {}) as Record<string, unknown>;
                return rest as unknown as AppState;
            },
        }
    )
);
