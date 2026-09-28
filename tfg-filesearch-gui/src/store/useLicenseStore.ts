import { create } from 'zustand';
import { LicenseState } from '../types';

interface LicenseStore {
    /** Null until loaded, and outside the desktop app. */
    license: LicenseState | null;
    refresh: () => Promise<void>;
    setLicense: (license: LicenseState) => void;
}

export const useLicenseStore = create<LicenseStore>()((set) => ({
    license: null,
    refresh: async () => {
        if (!window.electronAPI) return;
        try {
            set({ license: await window.electronAPI.getLicense() });
        } catch {
            // Leave the last known state; the banner simply does not show.
        }
    },
    setLicense: (license) => set({ license }),
}));
