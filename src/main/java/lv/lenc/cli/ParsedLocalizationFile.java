package lv.lenc.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ParsedLocalizationFile {

    private final LinkedHashMap<String, String> entries;
    private final List<Integer> malformedLineNumbers;
    private final List<String> duplicateKeys;

    public ParsedLocalizationFile(Map<String, String> entries,
                                  List<Integer> malformedLineNumbers,
                                  List<String> duplicateKeys) {
        this.entries = new LinkedHashMap<>(entries);
        this.malformedLineNumbers = List.copyOf(malformedLineNumbers);
        this.duplicateKeys = List.copyOf(duplicateKeys);
    }

    public LinkedHashMap<String, String> getEntries() {
        return new LinkedHashMap<>(entries);
    }

    public List<Integer> getMalformedLineNumbers() {
        return malformedLineNumbers;
    }

    public List<String> getDuplicateKeys() {
        return duplicateKeys;
    }
}
