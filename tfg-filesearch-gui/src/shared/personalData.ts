/**
 * Kinds of data the search engine finds in documents (EntityType in the
 * backend). Shared by the app and the main process, which writes the reports.
 */

export const DATA_TYPE_LABELS: Readonly<Record<string, string>> = {
    dni: 'DNI',
    nie: 'NIE',
    cif: 'CIF',
    iban: 'IBAN',
    telefono: 'Teléfono',
    email: 'Correo',
    procedimiento: 'Procedimiento',
    salud: 'Datos de salud',
};

export function dataTypeLabel(type: string): string {
    return DATA_TYPE_LABELS[type] ?? type;
}

/** Personal data of a natural person, shown as a warning on results. */
export const PERSONAL_DATA_TYPES: ReadonlySet<string> = new Set(['dni', 'nie', 'iban', 'telefono', 'email', 'salud']);

/** Characters [start, end) of a preview text hold personal data of this kind. */
export interface PersonalDataSpan {
    start: number;
    end: number;
    type: string;
}

/** One document in a data protection report. */
export interface ReportDocument {
    path: string;
    filename: string;
    extension: string;
    modifiedAt?: string;
    /** Contains the person's full name. */
    byName: boolean;
    /** Contains the person's identifier. */
    byIdentifier: boolean;
    dataTypes: string[];
}

/** Every document that mentions a person, for an access or erasure request (RGPD arts. 15 and 17). */
export interface PersonalDataReport {
    name?: string;
    /** Identifier as normalised by the engine, e.g. "12345678Z". */
    identifier?: string;
    identifierType?: string;
    generatedAt: string;
    total: number;
    /** More documents matched than the report lists. */
    truncated: boolean;
    documents: ReportDocument[];
}
