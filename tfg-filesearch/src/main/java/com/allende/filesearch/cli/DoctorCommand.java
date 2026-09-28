package com.allende.filesearch.cli;

import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.IndexSummary;
import com.allende.filesearch.utils.FileUtils;
import com.allende.filesearch.tika.OcrSupport;
import org.apache.tika.mime.MediaType;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "doctor", description = "Check the installation and the index", mixinStandardHelpOptions = true)
public class DoctorCommand implements Callable<Integer> {

    /** Formats the product promises; if any is missing the build lost Tika parsers. */
    private static final String[] REQUIRED_TYPES = {
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/rtf",
            "application/vnd.oasis.opendocument.text",
            "text/html",
            "application/vnd.ms-outlook",
            "message/rfc822",
            "application/zip",
    };

    @Override
    public Integer call() {
        System.out.println("Running System Doctor...");
        System.out.println("------------------------------------------------------------");
        boolean allOk = true;

        System.out.println("[INFO] Java " + System.getProperty("java.version"));

        DependencyContainer container = DependencyContainer.getInstance();
        var supported = container.getDocumentExtractor().supportedTypes();
        for (String type : REQUIRED_TYPES) {
            boolean ok = supported.contains(MediaType.parse(type));
            System.out.printf("[%-4s] Text extraction for %s%n", ok ? "OK" : "FAIL", type);
            allOk &= ok;
        }

        // Missing OCR is a warning: everything else works, scanned documents are found by name only.
        OcrSupport.Status ocr = container.getDocumentExtractor().ocrStatus();
        if (ocr.available()) {
            System.out.println("[OK  ] OCR for scanned documents (" + container.getConfig().getOcr().getLanguage() + ")");
        } else {
            System.out.println("[WARN] OCR for scanned documents not available: " + ocr.reason());
        }

        Path location = container.getIndexDirectory();
        System.out.println("[INFO] Index location: " + location);
        try (DocumentIndex index = container.openIndexReadOnly()) {
            IndexSummary summary = index.summary();
            if (summary.exists()) {
                System.out.printf("[OK  ] Index contains %d documents (%s)%n",
                        summary.documentCount(), FileUtils.formatFileSize(summary.sizeBytes()));
            } else {
                System.out.println("[INFO] Nothing indexed yet. Run 'update-index <folder>'.");
            }
        } catch (Exception e) {
            System.out.println("[FAIL] Could not open the index: " + e.getMessage());
            allOk = false;
        }

        Path parent = location.getParent();
        boolean writable = Files.isDirectory(location) ? Files.isWritable(location)
                : parent == null || !Files.exists(parent) || Files.isWritable(parent);
        System.out.printf("[%-4s] Index location is writable%n", writable ? "OK" : "FAIL");
        allOk &= writable;

        System.out.println("------------------------------------------------------------");
        System.out.println(allOk ? "System is healthy." : "System has issues. Please resolve the failures above.");
        return allOk ? 0 : 1;
    }
}
