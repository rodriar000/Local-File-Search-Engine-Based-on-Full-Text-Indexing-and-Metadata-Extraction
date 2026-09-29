import { describe, expect, it } from 'vitest';
import { HIGHLIGHT_POST, HIGHLIGHT_PRE, splitHighlight, splitPreview, stripHighlight } from './highlight';

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

describe('splitPreview', () => {
    it('marks personal data, also when it is a search match', () => {
        const text = `DNI ${mark('12345678Z')}, tel. 612345678.`;
        const dni = text.indexOf('12345678Z');
        const phone = text.indexOf('612345678');
        expect(splitPreview(text, [
            { start: phone, end: phone + 9, type: 'telefono' },
            { start: dni, end: dni + 9, type: 'dni' },
        ])).toEqual([
            { text: 'DNI ', match: false },
            { text: '12345678Z', match: true, personal: 'dni' },
            { text: ', tel. ', match: false },
            { text: '612345678', match: false, personal: 'telefono' },
            { text: '.', match: false },
        ]);
    });

    it('splits a search match that covers only part of the personal data', () => {
        const text = `ES91 ${mark('2100')} 0418`;
        expect(splitPreview(text, [{ start: 0, end: text.length, type: 'iban' }])).toEqual([
            { text: 'ES91 ', match: false, personal: 'iban' },
            { text: '2100', match: true, personal: 'iban' },
            { text: ' 0418', match: false, personal: 'iban' },
        ]);
    });

    it('ignores positions outside the text and behaves like splitHighlight without spans', () => {
        const text = `a ${mark('b')} c`;
        expect(splitPreview(text, [{ start: 50, end: 60, type: 'dni' }, { start: 3, end: 3, type: 'dni' }]))
            .toEqual(splitHighlight(text));
    });
});
