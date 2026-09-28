import React from 'react';
import { splitHighlight } from '../../shared/highlight';

interface HighlightedTextProps {
    fragment: string;
}

/** Renders a highlight fragment as text nodes; document content is never parsed as HTML. */
export const HighlightedText: React.FC<HighlightedTextProps> = ({ fragment }) => (
    <>
        {splitHighlight(fragment).map((segment, i) =>
            segment.match ? (
                <mark key={i} className="bg-yellow-200 text-black rounded px-0.5">
                    {segment.text}
                </mark>
            ) : (
                <React.Fragment key={i}>{segment.text}</React.Fragment>
            )
        )}
    </>
);
