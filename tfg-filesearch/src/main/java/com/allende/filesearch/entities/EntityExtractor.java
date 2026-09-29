package com.allende.filesearch.entities;

import java.math.BigInteger;
import java.time.Year;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds identifiers and other personal data in document text with fixed rules:
 * DNI, NIE and CIF (with their control character), IBAN (mod 97), Spanish phone
 * numbers, e-mail addresses, case numbers ("procedimiento 456/2024") and words
 * that point to health data. Nothing leaves the computer and no model is used.
 *
 * <p>Identifiers with a check digit are only reported when it is correct, so
 * random numbers are not mistaken for them. Every value is normalised
 * ("12.345.678-z" becomes "12345678Z") so any spelling finds the same documents.
 */
public final class EntityExtractor {

    /** Bump when the rules change, so indexed documents are analysed again. */
    public static final int VERSION = 1;
    /** Distinct values kept per document; enough for any real case file. */
    public static final int MAX_VALUES_PER_DOCUMENT = 2_000;

    /** A value found in the text, at [start, end). */
    public record Entity(EntityType type, String value, int start, int end) {
        /** Term stored in the index, e.g. "dni:12345678Z". */
        public String term() {
            return EntityExtractor.term(type, value);
        }
    }

    public static String term(EntityType type, String value) {
        return type.key() + ":" + value;
    }

    private static final String BEFORE = "(?<![\\p{L}\\p{N}])";
    private static final String AFTER = "(?![\\p{L}\\p{N}])";
    /** Control letter: joined, after a hyphen, or after a space when in capitals ("pagó 1234567 a Juan" is not a DNI). */
    private static final String LETTER = "(?:-?([A-Za-z])| ([A-Z]))";

    private static final Pattern IBAN = Pattern.compile(
            BEFORE + "([A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?)" + AFTER);
    private static final Pattern EMAIL = Pattern.compile(
            BEFORE + "([A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,})" + AFTER);
    private static final Pattern NIE = Pattern.compile(
            BEFORE + "([XYZxyz])[ -]?(\\d\\.?\\d{3}\\.?\\d{3})" + LETTER + AFTER);
    private static final Pattern DNI = Pattern.compile(
            BEFORE + "(\\d{1,2}\\.?\\d{3}\\.?\\d{3})" + LETTER + AFTER);
    private static final Pattern CIF = Pattern.compile(
            BEFORE + "([ABCDEFGHJNPQRSUVW])[ -]?(\\d{2}\\.?\\d{3}\\.?\\d{2})[ .-]?([0-9A-J])" + AFTER);
    /** Needs a word such as "procedimiento" or "autos" before the number, so laws ("Ley 1/2000") are left out. */
    private static final Pattern PROCEDIMIENTO = Pattern.compile(
            "(?iu)" + BEFORE + "(?:procedimientos?|proc\\.|autos|recursos?|rec\\.|juicios?(?:\\s+(?:ordinario|verbal|cambiario|monitorio))?"
                    + "|monitorio|ejecuci[oó]n(?:\\s+de\\s+t[ií]tulos?\\s+(?:judicial|no\\s+judicial)(?:es)?)?|diligencias(?:\\s+previas|\\s+urgentes)?"
                    + "|d\\.\\s?p\\.|sumario|rollo|expediente|exp\\.|apelaci[oó]n|casaci[oó]n|concurso|p\\.\\s?o\\.|j\\.\\s?v\\.)"
                    + "[^\\p{N}\\n]{0,25}?" + BEFORE + "(\\d{1,6})\\s?/\\s?(\\d{4}|\\d{2})" + AFTER);
    private static final Pattern BARE_PROCEDIMIENTO = Pattern.compile("^\\s*(\\d{1,6})\\s?/\\s?(\\d{4}|\\d{2})\\s*$");
    /** Spaces or hyphens between digits; "600.000.000" is an amount, not a phone. */
    private static final Pattern TELEFONO = Pattern.compile(
            "(?<![\\p{L}\\p{N}+])((?:\\(?(?:\\+|00)34\\)?[ -]?)?[6789](?:[ -]?\\d){8})" + AFTER);
    private static final Pattern SALUD = Pattern.compile(
            "(?iu)" + BEFORE + "(diagn[oó]stic[oa]s?|historias?\\s+cl[ií]nicas?|historial\\s+m[eé]dico|informes?\\s+m[eé]dicos?"
                    + "|bajas?\\s+m[eé]dicas?|incapacidad\\s+(?:temporal|permanente)|discapacidad(?:es)?|minusval[ií]as?"
                    + "|enfermedad(?:es)?|patolog[ií]as?|tratamientos?\\s+m[eé]dicos?|psiqui[aá]tric[oa]s?|VIH|embarazos?"
                    + "|medicaci[oó]n|secuelas?|lesiones)" + AFTER);

    private static final String DNI_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private EntityExtractor() {
    }

    /**
     * Every entity in {@code text}, in text order. Where two rules match the same
     * characters the more specific one wins (an IBAN is not also read as a phone).
     */
    public static List<Entity> find(String text) {
        List<Entity> found = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return found;
        }
        BitSet taken = new BitSet(text.length());

        Matcher m = IBAN.matcher(text);
        while (m.find()) {
            String iban = m.group(1).replace(" ", "");
            if (isValidIban(iban)) {
                add(found, taken, EntityType.IBAN, iban, m.start(1), m.end(1));
            }
        }
        m = EMAIL.matcher(text);
        while (m.find()) {
            add(found, taken, EntityType.EMAIL, m.group(1).toLowerCase(Locale.ROOT), m.start(1), m.end(1));
        }
        m = NIE.matcher(text);
        while (m.find()) {
            String nie = nie(m.group(1), m.group(2), letter(m, 3));
            if (nie != null) {
                add(found, taken, EntityType.NIE, nie, m.start(), m.end());
            }
        }
        m = DNI.matcher(text);
        while (m.find()) {
            String dni = dni(m.group(1), letter(m, 2));
            if (dni != null) {
                add(found, taken, EntityType.DNI, dni, m.start(), m.end());
            }
        }
        m = CIF.matcher(text);
        while (m.find()) {
            String cif = cif(m.group(1), m.group(2), m.group(3));
            if (cif != null) {
                add(found, taken, EntityType.CIF, cif, m.start(), m.end());
            }
        }
        m = PROCEDIMIENTO.matcher(text);
        while (m.find()) {
            add(found, taken, EntityType.PROCEDIMIENTO, procedimiento(m.group(1), m.group(2)), m.start(1), m.end(2));
        }
        m = TELEFONO.matcher(text);
        while (m.find()) {
            String digits = m.group(1).replaceAll("\\D", "");
            add(found, taken, EntityType.TELEFONO, digits.substring(digits.length() - 9), m.start(1), m.end(1));
        }
        m = SALUD.matcher(text);
        while (m.find()) {
            add(found, taken, EntityType.SALUD, "", m.start(1), m.end(1));
        }
        found.sort(Comparator.comparingInt(Entity::start));
        return found;
    }

    /** Index terms for a document: its distinct values, without the health words. */
    public static Set<String> terms(List<Entity> entities) {
        Set<String> terms = new LinkedHashSet<>();
        for (Entity entity : entities) {
            if (entity.type() != EntityType.SALUD) {
                terms.add(entity.term());
                if (terms.size() >= MAX_VALUES_PER_DOCUMENT) {
                    break;
                }
            }
        }
        return terms;
    }

    /**
     * Reads what a user typed in the "contains" filter or the RGPD report as an
     * identifier. A bare "456/2024" is taken as a case number.
     *
     * @return the identifier, or null when the text is not one
     */
    public static Entity parseIdentifier(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        Matcher bare = BARE_PROCEDIMIENTO.matcher(input);
        if (bare.matches()) {
            return new Entity(EntityType.PROCEDIMIENTO, procedimiento(bare.group(1), bare.group(2)), 0, input.length());
        }
        // Capitals so that "es91 2100…" or "b12345674" are read like printed identifiers.
        String typed = input.strip().toUpperCase(Locale.ROOT);
        for (Entity entity : find(typed)) {
            boolean wholeInput = entity.start() == 0 && entity.end() == typed.length();
            // "procedimiento 456/2024": the value is only the number.
            if (wholeInput || entity.type() == EntityType.PROCEDIMIENTO) {
                return entity;
            }
        }
        return null;
    }

    private static void add(List<Entity> found, BitSet taken, EntityType type, String value, int start, int end) {
        int next = taken.nextSetBit(start);
        if (next != -1 && next < end) {
            return;
        }
        taken.set(start, end);
        found.add(new Entity(type, value, start, end));
    }

    // ---------------------------------------------------------------- checks

    private static String letter(Matcher m, int group) {
        return m.group(group) != null ? m.group(group) : m.group(group + 1);
    }

    static String dni(String digits, String letter) {
        String number = String.format("%8s", digits.replace(".", "")).replace(' ', '0');
        char expected = DNI_LETTERS.charAt(Integer.parseInt(number) % 23);
        char given = Character.toUpperCase(letter.charAt(0));
        return given == expected ? number + given : null;
    }

    static String nie(String prefix, String digits, String letter) {
        char first = Character.toUpperCase(prefix.charAt(0));
        String number = digits.replace(".", "");
        int value = Integer.parseInt("XYZ".indexOf(first) + number);
        char given = Character.toUpperCase(letter.charAt(0));
        return given == DNI_LETTERS.charAt(value % 23) ? "" + first + number + given : null;
    }

    static String cif(String letter, String digits, String control) {
        String number = digits.replace(".", "");
        int sum = 0;
        for (int i = 0; i < number.length(); i++) {
            int digit = number.charAt(i) - '0';
            if (i % 2 == 0) {
                int doubled = digit * 2;
                sum += doubled / 10 + doubled % 10;
            } else {
                sum += digit;
            }
        }
        int check = (10 - sum % 10) % 10;
        char expectedLetter = "JABCDEFGHI".charAt(check);
        char given = control.charAt(0);
        char kind = letter.charAt(0);
        boolean ok;
        if ("PQRSNW".indexOf(kind) >= 0) {
            ok = given == expectedLetter;
        } else if ("ABEH".indexOf(kind) >= 0) {
            ok = given == (char) ('0' + check);
        } else {
            ok = given == expectedLetter || given == (char) ('0' + check);
        }
        return ok ? kind + number + given : null;
    }

    static boolean isValidIban(String iban) {
        if (iban.length() < 15 || iban.length() > 34 || (iban.startsWith("ES") && iban.length() != 24)) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue() == 1;
    }

    /** "0456/24" becomes "456/2024"; two-digit years are read as the closest past year. */
    static String procedimiento(String number, String year) {
        String n = number.replaceFirst("^0+(?=\\d)", "");
        String y = year;
        if (year.length() == 2) {
            int twoDigits = Integer.parseInt(year);
            int current = Year.now().getValue();
            int century = current / 100 * 100;
            y = String.valueOf(century + twoDigits > current ? century - 100 + twoDigits : century + twoDigits);
        }
        return n + "/" + y;
    }
}
