package com.allende.filesearch.cli;

import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.IndexSummary;
import com.allende.filesearch.utils.FileUtils;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
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
    };

    @Override
    public Integer call() {
        System.out.println("Running System Doctor...");
        System.out.println("------------------------------------------------------------");
        boolean allOk = true;

        System.out.println("[INFO] Java " + System.getProperty("java.version"));

        var supported = new AutoDetectParser().getSupportedTypes(new ParseContext());
        for (String type : REQUIRED_TYPES) {
            boolean ok = supported.contains(org.apache.tika.mime.MediaType.parse(type));
            System.out.printf("[%-4s] Text extraction for %s%n", ok ? "OK" : "FAIL", type);
            allOk &= ok;
        }

        DependencyContainer container = DependencyContainer.getInstance();
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
