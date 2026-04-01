package lv.lenc.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LocalizationTextIO {

    private static final Pattern LOCALE_DIR = Pattern.compile("^[A-Za-z]{4}\\.SC2Data$", Pattern.CASE_INSENSITIVE);

    private LocalizationTextIO() {
    }

    public static ParsedLocalizationFile readKeyValueFile(File file) throws IOException {
        Objects.requireNonNull(file, "file");

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        List<Integer> malformedLineNumbers = new ArrayList<>();
        List<String> duplicateKeys = new ArrayList<>();

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null || line.isBlank()) {
                continue;
            }

            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                malformedLineNumbers.add(index + 1);
                continue;
            }

            String key = parts[0];
            String value = parts[1];
            if (entries.containsKey(key)) {
                duplicateKeys.add(key);
                continue;
            }

            entries.put(key, value);
        }

        return new ParsedLocalizationFile(entries, malformedLineNumbers, duplicateKeys);
    }

    public static Map<String, String> parseKeyValue(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyMap();
        }

        return Arrays.stream(text.split("\\r?\\n"))
                .map(line -> line.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(java.util.stream.Collectors.toMap(
                        parts -> parts[0],
                        parts -> parts[1],
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    public static DiscoveredFiles discoverLanguageFiles(File selectedFile) {
        Objects.requireNonNull(selectedFile, "selectedFile");

        Optional<File> rootOpt = findLocaleRoot(selectedFile);
        if (rootOpt.isEmpty()) {
            LinkedHashMap<String, File> single = new LinkedHashMap<>();
            single.put("input", selectedFile);
            return new DiscoveredFiles(single, null, selectedFile.getName(), false);
        }

        File localeRoot = rootOpt.get();
        File projectRoot = localeRoot.getParentFile();
        if (projectRoot == null) {
            LinkedHashMap<String, File> single = new LinkedHashMap<>();
            single.put(normalizeLocale(localeRoot.getName().substring(0, 4)), selectedFile);
            return new DiscoveredFiles(single, localeRoot.getAbsolutePath(), selectedFile.getName(), true);
        }

        File[] dirs = projectRoot.listFiles(file ->
                file.isDirectory() && file.getName().matches("(?i)[a-z]{4}\\.sc2data.*")
        );
        if (dirs == null || dirs.length == 0) {
            LinkedHashMap<String, File> single = new LinkedHashMap<>();
            single.put(normalizeLocale(localeRoot.getName().substring(0, 4)), selectedFile);
            return new DiscoveredFiles(single, projectRoot.getAbsolutePath(), selectedFile.getName(), true);
        }

        File selectedLocalizedDir = findLocalizedDataDirectory(selectedFile);
        String relativePath;
        if (selectedLocalizedDir != null) {
            Path base = selectedLocalizedDir.toPath();
            relativePath = base.relativize(selectedFile.toPath()).toString();
        } else {
            relativePath = selectedFile.getName();
        }

        LinkedHashMap<String, File> langFiles = new LinkedHashMap<>();
        for (File dir : dirs) {
            String langCode = normalizeLocale(dir.getName().substring(0, 4));
            File localizedData = new File(dir, "LocalizedData");
            if (!localizedData.isDirectory()) {
                langFiles.put(langCode, null);
                continue;
            }

            File langFile = new File(localizedData, relativePath);
            langFiles.put(langCode, langFile.isFile() ? langFile : null);
        }

        return new DiscoveredFiles(langFiles, projectRoot.getAbsolutePath(), relativePath, true);
    }

    public static boolean isMeaningfulText(String value) {
        if (value == null) {
            return false;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        return !trimmed.equalsIgnoreCase("null");
    }

    private static File findLocalizedDataDirectory(File selectedFile) {
        File current = selectedFile;
        while (current != null) {
            if ("LocalizedData".equals(current.getName())) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    public static String normalizeLocale(String code) {
        if (code == null) {
            return "";
        }

        String cleaned = code.replaceAll("[^A-Za-z]", "");
        if (cleaned.length() == 2) {
            return cleaned.toLowerCase(Locale.ROOT);
        }
        if (cleaned.length() == 4) {
            return cleaned.substring(0, 2).toLowerCase(Locale.ROOT)
                    + cleaned.substring(2, 4).toUpperCase(Locale.ROOT);
        }
        return cleaned;
    }

    public static Optional<File> findLocaleRoot(File file) {
        File current = file;
        while (current != null) {
            if ("LocalizedData".equals(current.getName())) {
                File root = current.getParentFile();
                if (root != null && LOCALE_DIR.matcher(root.getName()).matches()) {
                    return Optional.of(root);
                }
            }
            current = current.getParentFile();
        }
        return Optional.empty();
    }
}