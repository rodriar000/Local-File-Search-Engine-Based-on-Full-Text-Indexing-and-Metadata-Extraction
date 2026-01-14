import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from './components/layout/Layout';
import { Dashboard } from './pages/Dashboard';
import { SearchPage } from './pages/Search';
import { SettingsPage } from './pages/Settings';
import { useEffect } from 'react';
import { useAppStore } from './store/useAppStore';

function App() {
    const { darkMode } = useAppStore();

    useEffect(() => {
        if (darkMode) {
            document.documentElement.classList.add('dark');
        } else {
            document.documentElement.classList.remove('dark');
        }
    }, [darkMode]);

    return (
        <HashRouter>
            <Routes>
                <Route path="/" element={<Layout />}>
                    <Route index element={<Dashboard />} />
                    <Route path="search" element={<SearchPage />} />
                    <Route path="stats" element={<Navigate to="/" replace />} />
                    <Route path="settings" element={<SettingsPage />} />
                </Route>
            </Routes>
        </HashRouter>
    );
}

export default App;
