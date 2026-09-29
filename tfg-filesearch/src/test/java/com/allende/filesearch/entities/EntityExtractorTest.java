package com.allende.filesearch.entities;

import com.allende.filesearch.entities.EntityExtractor.Entity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityExtractorTest {

    private static List<String> terms(String text) {
        return List.copyOf(EntityExtractor.terms(EntityExtractor.find(text)));
    }

    @Test
    void findsIdentifiersWrittenInTheUsualWays() {
        assertEquals(List.of("dni:12345678Z"), terms("D. Juan Pérez, con DNI 12.345.678-Z, mayor de edad"));
        assertEquals(List.of("dni:12345678Z"), terms("DNI: 12345678z."));
        assertEquals(List.of("nie:X1234567L"), terms("NIE X-1234567-L"));
        assertEquals(List.of("cif:B12345674"), terms("Construcciones Pérez, S.L., con CIF B-12345674"));
        assertEquals(List.of("iban:ES9121000418450200051332"), terms("IBAN: ES91 2100 0418 4502 0005 1332"));
        assertEquals(List.of("telefono:612345678"), terms("Tel. +34 612 34 56 78"));
        assertEquals(List.of("telefono:912345678"), terms("llamar al 91 234 56 78"));
        assertEquals(List.of("email:juan.perez@ejemplo.es"), terms("escribir a Juan.Perez@Ejemplo.es"));
    }

    @Test
    void ignoresNumbersWhoseControlCharacterIsWrong() {
        assertEquals(List.of(), terms("DNI 12345678A"));
        assertEquals(List.of(), terms("NIE X1234567A"));
        assertEquals(List.of(), terms("IBAN ES92 2100 0418 4502 0005 1332"));
        assertEquals(List.of(), terms("CIF B12345675"));
    }

    @Test
    void doesNotMistakeAmountsWordsOrLawsForData() {
        assertEquals(List.of(), terms("una indemnización de 600.000.000 euros"));
        assertEquals(List.of(), terms("pagó 1234567 a Juan"));
        assertEquals(List.of(), terms("según la Ley 1/2000, de 7 de enero, de Enjuiciamiento Civil"));
        assertEquals(List.of(), terms("referencia ABC12345678Z9"));
    }

    @Test
    void findsCaseNumbersAfterTheWordsThatIntroduceThem() {
        assertEquals(List.of("procedimiento:456/2024"), terms("Procedimiento Ordinario nº 456/2024"));
        assertEquals(List.of("procedimiento:1234/2023"), terms("en los autos de juicio verbal 1234 / 2023"));
        assertEquals(List.of("procedimiento:78/2019"), terms("Diligencias Previas núm. 0078/19"));
        assertEquals(List.of("procedimiento:12/2025"), terms("Recurso de apelación 12/2025"));
    }

    @Test
    void anIbanIsNotAlsoReadAsAPhoneNumber() {
        List<Entity> found = EntityExtractor.find("ES91 2100 0418 4502 0005 1332 y 612345678");
        assertEquals(2, found.size());
        assertEquals(EntityType.IBAN, found.get(0).type());
        assertEquals(EntityType.TELEFONO, found.get(1).type());
    }

    @Test
    void marksWordsThatPointToHealthDataWithoutIndexingThem() {
        String text = "Aporta informe médico y parte de baja médica por incapacidad temporal.";
        List<Entity> found = EntityExtractor.find(text);
        assertEquals(3, found.size());
        assertTrue(found.stream().allMatch(e -> e.type() == EntityType.SALUD));
        assertEquals("informe médico", text.substring(found.get(0).start(), found.get(0).end()));
        assertEquals(List.of(), List.copyOf(EntityExtractor.terms(found)));
    }

    @Test
    void readsWhatTheUserTypesAsAnIdentifier() {
        assertEquals("dni:12345678Z", EntityExtractor.parseIdentifier(" 12345678-z ").term());
        assertEquals("iban:ES9121000418450200051332", EntityExtractor.parseIdentifier("es91 2100 0418 4502 0005 1332").term());
        assertEquals("procedimiento:456/2024", EntityExtractor.parseIdentifier("456/2024").term());
        assertEquals("procedimiento:456/2024", EntityExtractor.parseIdentifier("Procedimiento 456/24").term());
        assertEquals("email:ana@despacho.es", EntityExtractor.parseIdentifier("Ana@Despacho.es").term());
        assertEquals("cif:B12345674", EntityExtractor.parseIdentifier("b12345674").term());
        assertNull(EntityExtractor.parseIdentifier("12345678A"));
        assertNull(EntityExtractor.parseIdentifier("Juan Pérez"));
        assertNull(EntityExtractor.parseIdentifier("DNI 12345678Z y más texto"));
    }
}
