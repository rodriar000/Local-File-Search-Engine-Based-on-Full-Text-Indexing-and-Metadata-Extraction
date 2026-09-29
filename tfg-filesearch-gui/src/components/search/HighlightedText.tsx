import React from 'react';
import { splitHighlight, splitPreview } from '../../shared/highlight';
import { dataTypeLabel, PersonalDataSpan } from '../../shared/personalData';

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

interface PreviewTextProps {
    text: string;
    personalData: PersonalDataSpan[];
    /** Whether to mark personal data. */
    showPersonalData: boolean;
}

/** Preview text with search matches and, optionally, personal data marked; never parsed as HTML. */
export const PreviewText: React.FC<PreviewTextProps> = ({ text, personalData, showPersonalData }) => (
    <>
        {splitPreview(text, showPersonalData ? personalData : []).map((segment, i) => {
            const content = segment.match
                ? <mark className="bg-yellow-200 text-black rounded px-0.5">{segment.text}</mark>
                : segment.text;
            return segment.personal ? (
                <span
                    key={i}
                    data-personal={segment.personal}
                    title={dataTypeLabel(segment.personal)}
                    className="bg-rose-100 text-rose-900 dark:bg-rose-900/40 dark:text-rose-100 underline decoration-rose-500 decoration-2 underline-offset-2 rounded-sm"
                >
                    {content}
                </span>
            ) : (
                <React.Fragment key={i}>{content}</React.Fragment>
            );
        })}
    </>
);
