package com.allende.filesearch.entities;

import java.util.Locale;
import java.util.Optional;

/** Kinds of data found in documents by {@link EntityExtractor}. */
public enum EntityType {
    DNI("DNI", true),
    NIE("NIE", true),
    CIF("CIF", false),
    IBAN("IBAN", true),
    TELEFONO("Teléfono", true),
    EMAIL("Correo electrónico", true),
    PROCEDIMIENTO("Nº de procedimiento", false),
    /** Words that point to health data (a special category under the RGPD); there is no value to search for. */
    SALUD("Datos de salud", true);

    private final String label;
    private final boolean personal;

    EntityType(String label, boolean personal) {
        this.label = label;
        this.personal = personal;
    }

    /** Name used in the index and the API, e.g. "dni". */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Spanish name shown to users. */
    public String label() {
        return label;
    }

    /** Personal data of a natural person, highlighted in the preview. */
    public boolean personal() {
        return personal;
    }

    public static Optional<EntityType> fromKey(String key) {
        for (EntityType type : values()) {
            if (type.key().equals(key)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
