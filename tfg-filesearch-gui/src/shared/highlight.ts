/**
 * Safe search-hit highlighting.
 *
 * The search engine returns snippets as raw document text with the matched
 * words wrapped in markers, without escaping any HTML the document contains.
 * Rendering them as HTML would let a crafted document run script in the app,
 * so the markers are Unicode private-use characters (the same ones as
 * DocumentIndex.HIGHLIGHT_PRE/POST in the backend) and snippets are rendered
 * as plain React text.
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
