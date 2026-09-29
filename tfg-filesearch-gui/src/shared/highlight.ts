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

export interface PreviewSegment extends HighlightSegment {
    /** Kind of personal data in this segment, if any (see shared/personalData.ts). */
    personal?: string;
}

/**
 * Split a preview text into segments that are search matches, personal data,
 * both or neither. Span positions count UTF-16 units of `text`, markers included.
 */
export function splitPreview(text: string, spans: { start: number; end: number; type: string }[]): PreviewSegment[] {
    const sorted = spans
        .filter((span) => span.start >= 0 && span.end > span.start && span.end <= text.length)
        .sort((a, b) => a.start - b.start);
    const segments: PreviewSegment[] = [];
    let match = false;
    let personal: string | undefined;
    let buffer = '';
    let next = 0;

    const flush = () => {
        if (buffer) segments.push(personal ? { text: buffer, match, personal } : { text: buffer, match });
        buffer = '';
    };

    for (let i = 0; i < text.length; i++) {
        while (next < sorted.length && sorted[next].end <= i) next++;
        const inside = next < sorted.length && sorted[next].start <= i ? sorted[next].type : undefined;
        if (inside !== personal) {
            flush();
            personal = inside;
        }
        const char = text[i];
        if (char === HIGHLIGHT_PRE || char === HIGHLIGHT_POST) {
            flush();
            match = char === HIGHLIGHT_PRE;
            continue;
        }
        buffer += char;
    }
    flush();
    return segments;
}
