/**
 * Safe search-hit highlighting.
 *
 * Elasticsearch returns highlight fragments as raw document text wrapped in
 * pre/post tags, without escaping any HTML the document itself contains.
 * Rendering those fragments as HTML would let a crafted document run script
 * in the app, so we ask Elasticsearch for Unicode private-use markers instead
 * of HTML tags and render the fragments as plain React text.
 */

export const HIGHLIGHT_PRE = '';
export const HIGHLIGHT_POST = '';

export interface HighlightSegment {
    text: string;
    match: boolean;
}

/** Split a highlighted fragment into plain and matched segments. */
export function splitHighlight(fragment: string): HighlightSegment[] {
    const segments: HighlightSegment[] = [];
    let match = false;
    let buffer = '';

    for (const char of fragment) {
        if (char === HIGHLIGHT_PRE || char === HIGHLIGHT_POST) {
            if (buffer) segments.push({ text: buffer, match });
            buffer = '';
            match = char === HIGHLIGHT_PRE;
            continue;
        }
        buffer += char;
    }
    if (buffer) segments.push({ text: buffer, match });

    return segments;
}

/** Remove highlight markers, e.g. before exporting a snippet. */
export function stripHighlight(fragment: string): string {
    return fragment.split(HIGHLIGHT_PRE).join('').split(HIGHLIGHT_POST).join('');
}
