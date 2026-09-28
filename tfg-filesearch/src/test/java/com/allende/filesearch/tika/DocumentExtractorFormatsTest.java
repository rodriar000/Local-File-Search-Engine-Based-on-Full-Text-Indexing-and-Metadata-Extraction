package com.allende.filesearch.tika;

import com.allende.filesearch.model.Config;
import com.allende.filesearch.model.Document;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocumentExtractorFormatsTest {

    @TempDir
    Path dir;

    private final DocumentExtractor extractor = new DocumentExtractor();

    // ------------------------------------------------------------------ email

    @Test
    void readsEmailHeadersBodyAndAttachments() throws Exception {
        String attachment = Base64.getMimeEncoder().encodeToString(
                "Anexo: liquidación de la fianza del local".getBytes(StandardCharsets.UTF_8));
        String eml = String.join("\r\n",
                "From: Ana García <ana@despacho.es>",
                "To: Luis Pérez <luis@cliente.com>",
                "Cc: archivo@despacho.es",
                "Subject: Requerimiento de pago",
                "Date: Mon, 12 Jan 2026 10:00:00 +0100",
                "MIME-Version: 1.0",
                "Content-Type: multipart/mixed; boundary=\"b1\"",
                "",
                "--b1",
                "Content-Type: text/plain; charset=UTF-8",
                "",
                "Le remitimos el burofax sobre las rentas impagadas.",
                "--b1",
                "Content-Type: text/plain; charset=UTF-8; name=\"anexo.txt\"",
                "Content-Disposition: attachment; filename=\"anexo.txt\"",
                "Content-Transfer-Encoding: base64",
                "",
                attachment,
                "--b1--",
                "");
        Path file = dir.resolve("requerimiento.eml");
        Files.writeString(file, eml, StandardCharsets.UTF_8);

        Document doc = extractor.extractDocument(file);

        assertThat(doc.getExtractionError()).isNull();
        assertThat(doc.getTitle()).isEqualTo("Requerimiento de pago");
        assertThat(doc.getAuthor()).contains("ana@despacho.es");
        assertThat(doc.getContent())
                .contains("Asunto: Requerimiento de pago")
                .contains("luis@cliente.com")
                .contains("archivo@despacho.es")
                .contains("burofax sobre las rentas impagadas")
                .contains("liquidación de la fianza");
    }

    @Test
    void outlookMessagesAreSupported() {
        assertThat(extractor.isSupported("msg")).isTrue();
        assertThat(extractor.supportedTypes()).contains(org.apache.tika.mime.MediaType.parse("application/vnd.ms-outlook"));
    }

    // -------------------------------------------------------------------- zip

    @Test
    void readsDocumentsInsideZipFiles() throws Exception {
        Path file = dir.resolve("expediente.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("escritos/demanda.txt"));
            zip.write("Demanda de juicio verbal por desahucio".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("poder.docx"));
            zip.write(docx("Poder general para pleitos"));
            zip.closeEntry();
        }

        Document doc = extractor.extractDocument(file);

        assertThat(doc.getContent())
                .contains("juicio verbal por desahucio")
                .contains("Poder general para pleitos")
                .contains("demanda.txt");
    }

    @Test
    void limitsTheNumberOfArchiveEntriesRead() throws Exception {
        Path file = dir.resolve("muchos.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (int i = 0; i < DocumentExtractor.MAX_EMBEDDED + 50; i++) {
                zip.putNextEntry(new ZipEntry("f" + i + ".txt"));
                zip.write(("marca" + i).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }

        Document doc = extractor.extractDocument(file);

        assertThat(doc.getContent()).contains("marca0").doesNotContain("marca" + (DocumentExtractor.MAX_EMBEDDED + 10));
    }

    // ----------------------------------------------------------- unreadable

    @Test
    void passwordProtectedPdfsAreKeptWithAReason() throws Exception {
        Path file = dir.resolve("protegido.pdf");
        try (PDDocument pdf = textPdf("Contenido confidencial")) {
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "secreto", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            pdf.protect(policy);
            pdf.save(file.toFile());
        }

        Document doc = extractor.extractDocument(file);

        assertThat(doc.getContent()).isEmpty();
        assertThat(doc.getExtractionError()).isEqualTo(DocumentExtractor.PASSWORD_PROTECTED);
        assertThat(doc.getFilename()).isEqualTo("protegido.pdf");
    }

    @Test
    void keepsParagraphsForThePreview() {
        assertThat(DocumentExtractor.normalize("Primero  párrafo\u0000\n\n\n\n  Segundo\t\tpárrafo "))
                .isEqualTo("Primero párrafo\n\nSegundo párrafo");
    }

    // -------------------------------------------------------------------- OCR

    @Test
    void scannedPdfsAreReadWithOcrWhenAvailable() throws Exception {
        DocumentExtractor withOcr = new DocumentExtractor(DocumentExtractor.DEFAULT_EXTENSIONS, new Config.OcrConfig());
        assumeTrue(withOcr.isOcrAvailable(), "Tesseract with Spanish data is not installed");

        Path file = dir.resolve("escaneado.pdf");
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(pdf, textImage("CONTRATO DE ARRENDAMIENTO"));
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.drawImage(image, 40, 600, 520, 60);
            }
            pdf.save(file.toFile());
        }

        assertThat(extractor.extractDocument(file).getContent()).as("without OCR").isEmpty();
        Document doc = withOcr.extractDocument(file);
        assertThat(doc.isOcrAvailable()).isTrue();
        assertThat(doc.getContent().toUpperCase()).contains("ARRENDAMIENTO");
    }

    @Test
    void scannedImagesAreReadWithOcrWhenAvailable() throws Exception {
        DocumentExtractor withOcr = new DocumentExtractor(DocumentExtractor.DEFAULT_EXTENSIONS, new Config.OcrConfig());
        assumeTrue(withOcr.isOcrAvailable(), "Tesseract with Spanish data is not installed");

        Path file = dir.resolve("foto.png");
        ImageIO.write(textImage("PODER NOTARIAL"), "png", file.toFile());

        assertThat(withOcr.extractDocument(file).getContent().toUpperCase()).contains("NOTARIAL");
    }

    @Test
    void reportsWhyOcrIsUnavailable() {
        Config.OcrConfig disabled = new Config.OcrConfig();
        disabled.setEnabled(false);
        assertThat(OcrSupport.check(disabled).available()).isFalse();

        Config.OcrConfig missing = new Config.OcrConfig();
        missing.setTesseractPath(dir.resolve("nowhere").toString());
        assertThat(OcrSupport.check(missing).reason()).contains("not found");

        Config.OcrConfig unknownLanguage = new Config.OcrConfig();
        unknownLanguage.setLanguage("xx_no_such_language");
        OcrSupport.Status status = OcrSupport.check(unknownLanguage);
        assertThat(status.available()).isFalse();
    }

    // ---------------------------------------------------------------- helpers

    private static BufferedImage textImage(String text) {
        BufferedImage image = new BufferedImage(2200, 250, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 90));
        g.drawString(text, 40, 160);
        g.dispose();
        return image;
    }

    private static PDDocument textPdf(String text) throws Exception {
        PDDocument pdf = new PDDocument();
        PDPage page = new PDPage();
        pdf.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 12);
            content.newLineAtOffset(50, 700);
            content.showText(text);
            content.endText();
        }
        return pdf;
    }

    static byte[] docx(String text) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (List<String> part : List.of(
                    List.of("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"),
                    List.of("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"),
                    List.of("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>" + text + "</w:t></w:r></w:p></w:body></w:document>"))) {
                zip.putNextEntry(new ZipEntry(part.get(0)));
                zip.write(part.get(1).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
