import { describe, expect, it } from 'vitest';
import { HIGHLIGHT_POST, HIGHLIGHT_PRE, splitHighlight, stripHighlight } from './highlight';

const mark = (text: string) => `${HIGHLIGHT_PRE}${text}${HIGHLIGHT_POST}`;

describe('splitHighlight', () => {
    it('splits a fragment into plain and matched segments', () => {
        expect(splitHighlight(`contrato de ${mark('arrendamiento')} con fianza`)).toEqual([
            { text: 'contrato de ', match: false },
            { text: 'arrendamiento', match: true },
            { text: ' con fianza', match: false },
        ]);
    });

    it('keeps HTML in document text as literal text', () => {
        const payload = '<img src=x onerror="alert(1)">';
        expect(splitHighlight(`${payload} ${mark('clave')}`)).toEqual([
            { text: `${payload} `, match: false },
            { text: 'clave', match: true },
        ]);
    });

    it('returns plain text unchanged and handles empty input', () => {
        expect(splitHighlight('sin coincidencias')).toEqual([{ text: 'sin coincidencias', match: false }]);
        expect(splitHighlight('')).toEqual([]);
    });

    it('handles adjacent matches and characters outside the BMP', () => {
        expect(splitHighlight(`${mark('a')}${mark('b')} 📄`)).toEqual([
            { text: 'a', match: true },
            { text: 'b', match: true },
            { text: ' 📄', match: false },
        ]);
    });
});

describe('stripHighlight', () => {
    it('removes all markers', () => {
        expect(stripHighlight(`${mark('uno')} y ${mark('dos')}`)).toBe('uno y dos');
    });
});
