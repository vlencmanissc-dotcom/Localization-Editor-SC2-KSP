package lv.lenc.cli;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LocalizationDiagnosticsService {

    public DiagnosticsReport analyzeMissingTexts(File selectedFile) {
        Objects.requireNonNull(selectedFile, "selectedFile");

        DiscoveredFiles discovered = LocalizationTextIO.discoverLanguageFiles(selectedFile);
        LinkedHashMap<String, File> languageFiles = discovered.getLanguageFiles();
        LinkedHashMap<String, ParsedLocalizationFile> parsedByLanguage = new LinkedHashMap<>();
        List<DiagnosticIssue> issues = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();

        for (Map.Entry<String, File> entry : languageFiles.entrySet()) {
            String language = entry.getKey();
            File file = entry.getValue();

            if (file == null) {
                issues.add(new DiagnosticIssue(
                        Severity.WARNING,
                        "MISSING_FILE",
                        language,
                        null,
                        null,
                        "Missing localized file for language " + language
                ));
                continue;
            }

            try {
                ParsedLocalizationFile parsed = LocalizationTextIO.readKeyValueFile(file);
                parsedByLanguage.put(language, parsed);
                allKeys.addAll(parsed.getEntries().keySet());

                for (Integer lineNumber : parsed.getMalformedLineNumbers()) {
                    issues.add(new DiagnosticIssue(
                            Severity.WARNING,
                            "MALFORMED_LINE",
                            language,
                            null,
                            lineNumber,
                            "Malformed line in " + language + " at line " + lineNumber
                    ));
                }

                for (String duplicateKey : parsed.getDuplicateKeys()) {
                    issues.add(new DiagnosticIssue(
                            Severity.WARNING,
                            "DUPLICATE_KEY",
                            language,
                            duplicateKey,
                            null,
                            "Duplicate key in " + language + ": " + duplicateKey
                    ));
                }
            } catch (IOException ex) {
                issues.add(new DiagnosticIssue(
                        Severity.ERROR,
                        "READ_ERROR",
                        language,
                        null,
                        null,
                        "Failed to read " + language + ": " + ex.getMessage()
                ));
            }
        }

        for (Map.Entry<String, ParsedLocalizationFile> entry : parsedByLanguage.entrySet()) {
            String language = entry.getKey();
            Map<String, String> values = entry.getValue().getEntries();

            for (String key : allKeys) {
                if (!values.containsKey(key)) {
                    issues.add(new DiagnosticIssue(
                            Severity.WARNING,
                            "MISSING_KEY",
                            language,
                            key,
                            null,
                            "Missing key in " + language + ": " + key
                    ));
                    continue;
                }

                String value = values.get(key);
                if (!LocalizationTextIO.isMeaningfulText(value)) {
                    issues.add(new DiagnosticIssue(
                            Severity.WARNING,
                            "BLANK_VALUE",
                            language,
                            key,
                            null,
                            "Blank or null-like value in " + language + ": " + key
                    ));
                }
            }
        }

        issues.sort(Comparator
                .comparing((DiagnosticIssue issue) -> issue.getSeverity().name())
                .thenComparing(issue -> issue.getLanguage() == null ? "" : issue.getLanguage())
                .thenComparing(issue -> issue.getCode())
                .thenComparing(issue -> issue.getKey() == null ? "" : issue.getKey())
                .thenComparing(issue -> issue.getLine() == null ? -1 : issue.getLine()));

        int errorCount = (int) issues.stream().filter(issue -> issue.getSeverity() == Severity.ERROR).count();
        int warningCount = (int) issues.stream().filter(issue -> issue.getSeverity() == Severity.WARNING).count();

        return new DiagnosticsReport(
                selectedFile.getAbsolutePath(),
                discovered.getProjectRoot(),
                discovered.getRelativePath(),
                discovered.isSc2Layout(),
                new ArrayList<>(languageFiles.keySet()),
                warningCount,
                errorCount,
                issues
        );
    }

}