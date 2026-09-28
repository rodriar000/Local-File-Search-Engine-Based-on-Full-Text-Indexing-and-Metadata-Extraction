import React from 'react';
import { Search, FileQuestion, Inbox } from 'lucide-react';

interface EmptyStateProps {
    type: 'no-results' | 'no-query' | 'welcome';
    query?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({ type, query }) => {
    const getContent = () => {
        switch (type) {
            case 'welcome':
                return {
                    icon: <Search className="w-20 h-20 text-gray-400 dark:text-gray-500" />,
                    title: 'Busca en tus documentos',
                    description: 'Escribe arriba para buscar en escritos, PDF, correos y demás documentos.',
                    tip: 'Puedes buscar palabras del contenido, nombres de archivo o frases entre comillas'
                };
            case 'no-query':
                return {
                    icon: <Inbox className="w-20 h-20 text-gray-400 dark:text-gray-500" />,
                    title: 'Listo para buscar',
                    description: 'Escribe qué quieres encontrar',
                    tip: null
                };
            case 'no-results':
                return {
                    icon: <FileQuestion className="w-20 h-20 text-gray-400 dark:text-gray-500" />,
                    title: 'Sin resultados',
                    description: query ? `Ningún documento coincide con «${query}»` : 'Prueba a cambiar la búsqueda o los filtros',
                    tip: 'Prueba con otras palabras o quita los filtros'
                };
        }
    };

    const content = getContent();

    return (
        <div className="flex flex-col items-center justify-center min-h-[40vh] text-center px-4">
            <div className="mb-6">
                {content.icon}
            </div>

            <h3 className="text-xl font-semibold mb-2 text-gray-900 dark:text-white">
                {content.title}
            </h3>

            <p className="text-gray-600 dark:text-gray-400 max-w-md mb-4">
                {content.description}
            </p>

            {content.tip && (
                <div className="mt-2 text-sm text-gray-500 dark:text-gray-400">
                    {content.tip}
                </div>
            )}
        </div>
    );
};
