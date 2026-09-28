import React from 'react';
import { Sidebar } from './Sidebar';
import { Outlet } from 'react-router-dom';
import { cn } from '../../lib/utils';
import { useAppStore } from '../../store/useAppStore';
import { LicenseBanner } from '../LicenseBanner';

export const Layout: React.FC = () => {
    const { isSidebarOpen } = useAppStore();

    return (
        <div className="flex h-screen bg-gray-50 dark:bg-gray-900 text-gray-900 dark:text-gray-100 overflow-hidden font-sans transition-colors duration-300">
            <Sidebar />

            <main
                className={cn(
                    "flex-1 flex flex-col min-w-0 overflow-hidden transition-all duration-300 ease-in-out",
                    isSidebarOpen ? "ml-64" : "ml-20"
                )}
            >
                <LicenseBanner />
                <div className="flex-1 overflow-y-auto p-8 custom-scrollbar">
                    <div className="max-w-7xl mx-auto w-full">
                        <Outlet />
                    </div>
                </div>
            </main>
        </div>
    );
};
