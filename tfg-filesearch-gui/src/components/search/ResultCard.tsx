import React from 'react';
import { FileText, Calendar, HardDrive, User, FileCode, FileJson, FileType, File, Mail, FileArchive, FileImage } from 'lucide-react';
import { motion } from 'framer-motion';
import { formatBytes } from '../../lib/utils';
import { SearchResult } from '../../types';
import { HighlightedText } from './HighlightedText';

interface ResultCardProps {
    hit: SearchResult['hits'][0];
    onClick: () => void;
}

const getFileIcon = (extension: string) => {
    switch (extension.toLowerCase()) {
        case 'pdf': return <FileType className="w-5 h-5 text-red-500" />;
        case 'docx': return <FileText className="w-5 h-5 text-blue-500" />;
        case 'txt': return <FileText className="w-5 h-5 text-gray-500" />;
        case 'json': return <FileJson className="w-5 h-5 text-yellow-500" />;
        case 'md': return <FileCode className="w-5 h-5 text-purple-500" />;
        case 'msg':
        case 'eml': return <Mail className="w-5 h-5 text-sky-600" />;
        case 'zip': return <FileArchive className="w-5 h-5 text-amber-600" />;
        case 'tif':
        case 'tiff':
        case 'jpg':
        case 'jpeg':
        case 'png': return <FileImage className="w-5 h-5 text-emerald-600" />;
        default: return <File className="w-5 h-5 text-gray-400" />;
    }
};

export const ResultCard = React.forwardRef<HTMLDivElement, ResultCardProps>(({ hit, onClick }, ref) => {
    const { document: doc, highlight } = hit;

    // Highlights carry marker characters, never HTML; see shared/highlight.ts
    const contentPreview = highlight?.content?.[0] ?? (doc.content ? doc.content.substring(0, 200) + '...' : '');
    const titlePreview = highlight?.title?.[0] ?? (doc.title || doc.filename);

    return (
        <motion.div
            ref={ref}
            layout
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            whileHover={{ scale: 1.01, boxShadow: "0 4px 12px rgba(0,0,0,0.05)" }}
            onClick={onClick}
            className="group bg-white dark:bg-gray-800 rounded-xl border border-gray-100 dark:border-gray-700 p-5 cursor-pointer transition-all duration-200"
        >
            <div className="flex items-start gap-4">
                <div className="p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg group-hover:bg-white dark:group-hover:bg-gray-600 transition-colors shadow-sm">
                    {getFileIcon(doc.extension)}
                </div>

                <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-1">
                        <h3 className="text-lg font-semibold text-blue-600 dark:text-blue-400 truncate pr-4">
                            <HighlightedText fragment={titlePreview} />
                        </h3>
                    </div>

                    <div className="text-xs text-gray-500 dark:text-gray-400 mb-3 flex items-center gap-4 font-mono">
                        <span className="flex items-center gap-1 truncate max-w-[300px]" title={doc.path}>
                            <HardDrive className="w-3 h-3" />
                            {doc.path}
                        </span>
                        <span className="flex items-center gap-1">
                            <Calendar className="w-3 h-3" />
                            {doc.modified_at ? new Date(doc.modified_at).toLocaleDateString('es-ES') : 'Sin fecha'}
                        </span>
                        <span className="flex items-center gap-1">
                            {formatBytes(doc.size)}
                        </span>
                        {doc.author && (
                            <span className="flex items-center gap-1">
                                <User className="w-3 h-3" />
                                {doc.author}
                            </span>
                        )}
                    </div>

                    <p className="text-sm text-gray-600 dark:text-gray-300 leading-relaxed line-clamp-2 font-serif">
                        <HighlightedText fragment={contentPreview} />
                    </p>
                </div>
            </div>
        </motion.div>
    );
});

ResultCard.displayName = "ResultCard";
