package com.allende.filesearch.index;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Folders and files skipped while indexing, from the {@code excludePatterns} setting.
 *
 * <p>Supported forms, matched against a single path element so they behave the
 * same on Windows and Unix: {@code **}{@code /name/**} skips every folder called
 * {@code name}; {@code **}{@code /name} skips every file called {@code name}.
 * {@code *} and {@code ?} work as wildcards inside the name, e.g. {@code **}{@code /~$*}
 * for Office lock files. Matching ignores case.
 */
public final class ExclusionRules {
    private final List<Pattern> folderNames = new ArrayList<>();
    private final List<Pattern> fileNames = new ArrayList<>();

    public ExclusionRules(List<String> patterns) {
        if (patterns == null) {
            return;
        }
        for (String raw : patterns) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String pattern = raw.trim().replace('\\', '/');
            if (pattern.startsWith("**/")) {
                pattern = pattern.substring(3);
            }
            if (pattern.endsWith("/**")) {
                addIfSingleName(folderNames, pattern.substring(0, pattern.length() - 3), raw);
            } else {
                addIfSingleName(fileNames, pattern, raw);
            }
        }
    }

    public boolean isExcludedFolder(Path folder) {
        Path name = folder.getFileName();
        return name != null && matchesAny(folderNames, name.toString());
    }

    public boolean isExcludedFile(Path file) {
        Path name = file.getFileName();
        return name != null && matchesAny(fileNames, name.toString());
    }

    private static void addIfSingleName(List<Pattern> target, String name, String raw) {
        if (name.isEmpty() || name.contains("/")) {
            throw new IllegalArgumentException("Unsupported exclude pattern \"" + raw
                    + "\". Use \"**/<folder>/**\" or \"**/<file name>\".");
        }
        target.add(globToRegex(name));
    }

    private static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static boolean matchesAny(List<Pattern> patterns, String name) {
        String candidate = name.toLowerCase(Locale.ROOT);
        for (Pattern pattern : patterns) {
            if (pattern.matcher(candidate).matches()) {
                return true;
            }
        }
        return false;
    }
}
