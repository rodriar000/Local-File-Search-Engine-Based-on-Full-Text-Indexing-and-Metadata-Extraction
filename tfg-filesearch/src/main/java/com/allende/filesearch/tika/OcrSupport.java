package com.allende.filesearch.tika;

import com.allende.filesearch.model.Config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Finds out whether scanned documents can be read: OCR must be enabled, the
 * tesseract program must run, and the data for every configured language must
 * be installed.
 */
public final class OcrSupport {

    /**
     * @param available true when OCR will be used
     * @param reason    why OCR is not available, for the doctor command; null when available
     */
    public record Status(boolean available, String reason) {
    }

    private OcrSupport() {
    }

    public static Status check(Config.OcrConfig config) {
        if (config == null || !config.isEnabled()) {
            return new Status(false, "OCR is disabled in the configuration");
        }
        List<String> command = List.of(executable(config), "--list-langs");
        String output;
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(15, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                return new Status(false, "tesseract did not run correctly");
            }
        } catch (IOException e) {
            return new Status(false, "tesseract was not found (install it or set ocr.tesseractPath)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Status(false, "interrupted while checking tesseract");
        }

        List<String> installed = output.lines().map(String::trim).toList();
        for (String language : config.getLanguage().split("\\+")) {
            if (!installed.contains(language.trim())) {
                return new Status(false, "tesseract language data \"" + language.trim() + "\" is not installed");
            }
        }
        return new Status(true, null);
    }

    static String executable(Config.OcrConfig config) {
        String name = System.getProperty("os.name", "").toLowerCase().startsWith("windows") ? "tesseract.exe" : "tesseract";
        String folder = config.getTesseractPath();
        return folder == null || folder.isBlank() ? name : Path.of(folder).resolve(name).toString();
    }
}
