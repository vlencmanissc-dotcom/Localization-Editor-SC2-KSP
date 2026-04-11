package lv.lenc;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import okhttp3.OkHttpClient;
/**
 * Exact-match glossary for SC2 localization.
 *
 * CSV format supported:
 * 1) Units:
 *    key;unit_id;enus;ruru;dede;eses;esmx;frfr;itit;kokr;plpl;ptbr;zhcn;zhtw;missing_locales;source_count
 *
 * 2) Buttons / Abilities:
 *    sometimes first line is:
 *    Column1;Column2;...
 *    then real header:
 *    key;enus;ruru;dede;eses;esmx;frfr;itit;kokr;plpl;ptbr;zhcn;zhtw
 *
 * Matching rule:
 * - same category
 * - same key
 * - same source language
 * - same normalized source text
 * - same target language
 *
 * Result:
 * - returns ready-made translation from glossary
 */
public final class GlossaryService {

    private static final String WORD_GLOSSARY_FILE = "sc2_word_glossary_KSP.txt";
    private static final String[] EDITABLE_GLOSSARY_FILES = {
            WORD_GLOSSARY_FILE,
            "Addition_UnitNames_Detailed_KSP.txt",
            "Addition_Weapons_Detailed_KSP.txt",
            "Addition_Abilities_Detailed_KSP.txt"
    };

    public enum Category {
        UNIT, BUTTON, ABILITY
    }

    private static final Set<String> LANGS = new LinkedHashSet<>(Arrays.asList(
            "enus", "ruru", "dede", "eses", "esmx", "frfr",
            "itit", "kokr", "plpl", "ptbr", "zhcn", "zhtw"
    ));

    private final Map<LookupKey, String> exactMap = new HashMap<>();
    private final Map<TextOnlyLookupKey, String> textOnlyMap = new HashMap<>();
    private final Map<TxtLookupKey, String> txtTextOnlyMap = new HashMap<>();
    private final Map<TxtLookupKey, String> wordTextOnlyMap = new HashMap<>();
    private final Map<TxtLookupKey, String> normalizedWordTextOnlyMap = new HashMap<>();
    private final Set<String> wordSources = new HashSet<>();
    private final BooleanProperty glossaryLoading = new SimpleBooleanProperty(false);
    private final BooleanProperty glossaryReady = new SimpleBooleanProperty(false);
    private final StringProperty glossaryStatus = new SimpleStringProperty("Glossary not load");
    private final List<GlossaryRow> rows = new ArrayList<>();
    private final Map<TermLookupKey, List<TermEntry>> termIndex = new HashMap<>();
    private static final Pattern BROKEN_SC2_TERM_TOKEN = Pattern.compile(
            "(?iu)(?:__\\s*)?SC2\\s*[_\\- ]*TERM\\s*[_\\- ]*(\\d+)\\s*(?:__)?"
    );
    private static final Pattern WORD_PATTERN = Pattern.compile("(?iu)\\p{L}[\\p{L}\\p{N}_'-]*");
    private static final int GEMINI_AI_INFLECTION_MAX_ITEMS = 24;
    private static final int GEMINI_AI_INFLECTION_MAX_CHARS = 4500;

    public GlossaryService() {
    }

    public static GlossaryService loadDefault() {
        GlossaryService svc = new GlossaryService();

        // names can be changed if you rename files later
        svc.loadFromResource("/glossary/Units_Localization_KSP.csv", Category.UNIT);
        svc.loadFromResource("/glossary/Button_Localization_KSP.csv", Category.BUTTON);
        svc.loadFromResource("/glossary/Ability_Localization_KSP.csv", Category.ABILITY);

        return svc;
    }

    public void loadFromResource(String resourcePath, Category category) {
        try (InputStream is = GlossaryService.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                AppLog.warn("[Glossary] Resource not found: " + resourcePath);
                return;
            }
            loadFromStream(is, category, resourcePath);
        } catch (Exception e) {
            AppLog.warn("[Glossary] Failed to load " + resourcePath + ": " + e.getMessage());
        }
    }

    public void loadTxtFromResource(String resourcePath) {
        try (InputStream is = GlossaryService.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                AppLog.warn("[Glossary] TXT resource not found: " + resourcePath);
                return;
            }
            if (isWordGlossaryFile(resourcePath)) {
                loadWordGlossaryFromStream(is, resourcePath);
            } else {
                loadTxtGlossaryFromStream(is, resourcePath);
            }
        } catch (Exception e) {
            AppLog.warn("[Glossary] Failed to load TXT glossary " + resourcePath + ": " + e.getMessage());
        }
    }

    public void loadFromStream(InputStream is, Category category, String sourceName) throws IOException {
        List<String[]> csvRows = readSemicolonCsv(is);

        if (csvRows.isEmpty()) {
            AppLog.warn("[Glossary] Empty CSV: " + sourceName);
            return;
        }

        int headerIndex = findRealHeaderIndex(csvRows);
        if (headerIndex < 0) {
            AppLog.warn("[Glossary] Header not found in: " + sourceName);
            return;
        }

        String[] header = csvRows.get(headerIndex);
        Map<String, Integer> col = indexColumns(header);

        if (!col.containsKey("key")) {
            AppLog.warn("[Glossary] No 'key' column in: " + sourceName);
            return;
        }

        int imported = 0;

        for (int i = headerIndex + 1; i < csvRows.size(); i++) {
            String[] row = csvRows.get(i);
            if (isRowBlank(row)) continue;

            String key = getCell(row, col.get("key"));
            if (isBlank(key)) continue;

            key = stripBom(key).trim();

            Map<String, String> textsByLang = new HashMap<>();

            for (String lang : LANGS) {
                Integer idx = col.get(lang);
                if (idx == null) continue;

                String txt = cleanGlossaryText(getCell(row, idx));
                if (!isBlank(txt)) {
                    textsByLang.put(normalizeLang(lang), txt.trim());
                }
            }

            if (!textsByLang.isEmpty()) {
                this.rows.add(new GlossaryRow(category, key, textsByLang));
            }

            for (String sourceLang : LANGS) {
                Integer srcIdx = col.get(sourceLang);
                if (srcIdx == null) continue;

                String sourceText = cleanGlossaryText(getCell(row, srcIdx));
                if (isBlank(sourceText)) continue;

                for (String targetLang : LANGS) {
                    if (targetLang.equals(sourceLang)) continue;

                    Integer trgIdx = col.get(targetLang);
                    if (trgIdx == null) continue;

                    String targetText = cleanGlossaryText(getCell(row, trgIdx));
                    if (isBlank(targetText)) continue;

                    LookupKey lk = new LookupKey(
                            category,
                            normalizeKey(key),
                            normalizeLang(sourceLang),
                            normalizeText(sourceText),
                            normalizeLang(targetLang)
                    );

                    TextOnlyLookupKey tlk = new TextOnlyLookupKey(
                            category,
                            normalizeLang(sourceLang),
                            normalizeText(sourceText),
                            normalizeLang(targetLang)
                    );

                    textOnlyMap.putIfAbsent(tlk, targetText.trim());
                    exactMap.putIfAbsent(lk, targetText.trim());
                    imported++;
                }
            }
        }

        AppLog.info("[Glossary] Loaded " + imported + " mappings from " + sourceName
                + " | exact keys = " + exactMap.size());
    }

    /**
     * Exact lookup by category + key + sourceLang + sourceText + targetLang
     */
    public String findExact(Category category,
                            String key,
                            String sourceLang,
                            String sourceText,
                            String targetLang) {
        if (category == null || isBlank(key) || isBlank(sourceLang) || isBlank(targetLang)) {
            return null;
        }

        String cleanedSource = cleanGlossaryText(sourceText);
        if (isBlank(cleanedSource)) return null;

        LookupKey lk = new LookupKey(
                category,
                normalizeKey(key),
                normalizeLang(sourceLang),
                normalizeText(cleanedSource),
                normalizeLang(targetLang)
        );

        String hit = exactMap.get(lk);
        return isBlank(hit) ? null : hit;
    }

    /**
     * Convenience method if category is inferred from key prefix.
     */
    public String findExact(String key,
                            String sourceLang,
                            String sourceText,
                            String targetLang) {
        Category category = detectCategory(key);
        if (category != null) {
            return findBestMatch(category, key, sourceLang, sourceText, targetLang);
        }

// fallback
        String cleanedSource = cleanGlossaryText(sourceText);
        if (isBlank(cleanedSource) || isBlank(sourceLang) || isBlank(targetLang)) {
            return null;
        }

        for (Category cat : Category.values()) {
            String exact = findExact(cat, key, sourceLang, sourceText, targetLang);
            if (!isBlank(exact)) return exact;

            TextOnlyLookupKey tlk = new TextOnlyLookupKey(
                    cat,
                    normalizeLang(sourceLang),
                    normalizeText(cleanedSource),
                    normalizeLang(targetLang)
            );
            String hit = textOnlyMap.get(tlk);
            if (!isBlank(hit)) return hit;
        }

        return null;
    }
    public String findBestMatch(String sourceLang, String sourceText, String targetLang) {
        if (isBlank(sourceLang) || isBlank(sourceText) || isBlank(targetLang)) {
            return null;
        }

        String cleaned = cleanGlossaryText(sourceText);
        if (isBlank(cleaned)) {
            return null;
        }

        if (normalizeLang(sourceLang).equals(normalizeLang(targetLang))) {
            return cleaned;
        }

        String singleWord = findWordMatch(sourceLang, cleaned, targetLang);
        if (!isBlank(singleWord)) {
            return singleWord;
        }

        return null;
    }
    public String findBestMatch(Category category, String key, String sourceLang, String sourceText, String targetLang) {
        return findBestMatch(sourceLang, sourceText, targetLang);
    }
    @SuppressWarnings("unused")
    private String translateFallback(String sourceLang, String sourceText, String targetLang) {
        try {
            String cleaned = cleanGlossaryText(sourceText);
            if (isBlank(cleaned)) {
                return null;
            }

            java.util.List<String> out = TranslationService.translatePreservingTags(
                    TranslationService.api,
                    java.util.Collections.singletonList(cleaned),
                    toLibreCode(sourceLang),
                    toLibreCode(targetLang)
            );

            if (out == null || out.isEmpty() || isBlank(out.get(0))) {
                return null;
            }

            return out.get(0);
        } catch (Exception e) {
            AppLog.warn("[GlossaryService] translateFallback failed: " + e.getMessage());
            return null;
        }
    }

    private String translateViaMt(String text, String sourceLang, String targetLang) {
        try {
            String cleaned = cleanGlossaryText(text);
            if (isBlank(cleaned)) {
                return null;
            }

            java.util.List<String> out = TranslationService.translatePreservingTags(
                    TranslationService.api,
                    java.util.Collections.singletonList(cleaned),
                    toLibreCode(sourceLang),
                    toLibreCode(targetLang)
            );

            if (out == null || out.isEmpty() || isBlank(out.get(0))) {
                return null;
            }

            return out.get(0);
        } catch (Exception e) {
            AppLog.warn("[GlossaryService] MT translation failed: " + e.getMessage());
            return null;
        }
    }

    private String toLibreCode(String lang) {
        String l = normalizeLang(lang);
        switch (l) {
            case "ruru": return "ru";
            case "enus": return "en";
            case "dede": return "de";
            case "eses": return "es";
            case "esmx": return "es";
            case "frfr": return "fr";
            case "itit": return "it";
            case "kokr": return "ko";
            case "plpl": return "pl";
            case "ptbr": return "pt";
            case "zhcn": return "zh";
            case "zhtw": return "zh";
            default:
                if (l.length() >= 2) {
                    return l.substring(0, 2);
                }
                return l;
        }
    }
    @SuppressWarnings("unused")
    private static String restoreSimpleMarkup(String original, String translated) {
        if (isBlank(original) || isBlank(translated)) {
            return translated;
        }

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(<c\\s+val=\"[^\"]+\">)(.*?)(</c>)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(original);

        if (m.find()) {
            return m.group(1) + translated + m.group(3);
        }

        return translated;
    }
    public String findBestMatch(String key,
                                String sourceLang,
                                String sourceText,
                                String targetLang) {
        String cleanedSource = cleanGlossaryText(sourceText);
        if (isBlank(cleanedSource) || isBlank(sourceLang) || isBlank(targetLang)) {
            return null;
        }

        if (normalizeLang(sourceLang).equals(normalizeLang(targetLang))) {
            return cleanedSource;
        }

        if (looksLikeReusableWord(cleanedSource)) {
            String wordHit = findWordMatch(sourceLang, cleanedSource, targetLang);
            if (!isBlank(wordHit)) {
                return wordHit;
            }
        }

        return null;
    }
    public static Category detectCategory(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(Locale.ROOT);
        if (k.startsWith("unit/name/")) return Category.UNIT;
        if (k.startsWith("button/name/")) return Category.BUTTON;
        if (k.startsWith("abil/name/")) return Category.ABILITY;
        return null;
    }

    public int size() {
        return exactMap.size();
    }

    // ---------- internals ----------

    private static List<String[]> readSemicolonCsv(InputStream is) throws IOException {
        List<String[]> rows = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = stripBom(line);
                rows.add(splitSemicolonLine(line));
            }
        }

        return rows;
    }

    /**
     * Minimal CSV splitter for semicolon-separated data with quotes.
     */
    private static String[] splitSemicolonLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ';' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }

        out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    private static int findRealHeaderIndex(List<String[]> rows) {
        for (int i = 0; i < Math.min(rows.size(), 10); i++) {
            String[] row = rows.get(i);
            if (row.length == 0) continue;

            String first = stripBom(safe(row, 0)).trim().toLowerCase(Locale.ROOT);
            if ("key".equals(first)) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, Integer> indexColumns(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String name = stripBom(header[i]).trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                map.put(name, i);
            }
        }
        return map;
    }

    /**
     * Removes trailing English comments:
     * "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ĀÄā€Ā¬Ä€Ć†Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā'Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä†ā€ Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā - Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¹ Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā /// Zhakul'Das Light Bridge" -> "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ĀÄā€Ā¬Ä€Ć†Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā'Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä†ā€ Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā - Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¹ Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā"
     */
    public static String cleanGlossaryText(String s) {
        if (s == null) return null;

        s = stripBom(s);
        s = s.replace('\u00A0', ' ');

        int pos = s.indexOf("///");
        if (pos >= 0) {
            s = s.substring(0, pos);
        }

        s = s.trim();
        s = s.replaceAll("\\s+", " ");

        if (s.isEmpty()) return null;
        if ("null".equalsIgnoreCase(s)) return null;

        return s;
    }

    private static String normalizeText(String s) {
        s = cleanGlossaryText(s);
        if (s == null) return "";

        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        s = s.replaceAll("[\\r\\n\\t]+", " ");
        s = s.replaceAll("\\s+", " ").trim();

        // normalize punctuation spacing a bit
        s = s.replaceAll("\\s+([,:;!?])", "$1");
        s = s.replaceAll("([,:;!?])(?!\\s|$)", "$1 ");

        return s.toLowerCase(Locale.ROOT);
    }

    private static String normalizeKey(String key) {
        String normalized = stripBom(key);
        return normalized == null ? "" : normalized.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeLang(String lang) {
        String normalized = stripBom(lang);
        return normalized == null ? "" : normalized.trim().toLowerCase(Locale.ROOT);
    }

    private static String getCell(String[] row, Integer idx) {
        if (idx == null) return null;
        return safe(row, idx);
    }

    private static String safe(String[] arr, int idx) {
        return idx >= 0 && idx < arr.length ? arr[idx] : "";
    }

    private static boolean isRowBlank(String[] row) {
        for (String s : row) {
            if (!isBlank(s)) return false;
        }
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }


    private static String normalizeSourceTermForLookup(String lang, String text) {
        String cleaned = cleanGlossaryText(text);
        if (isBlank(cleaned)) {
            return null;
        }

        String normLang = normalizeLang(lang);
        String[] parts = cleaned.split("\\s+");
        List<String> normalizedParts = new ArrayList<>();

        for (String part : parts) {
            String plain = stripEdgePunctuation(part);
            if (isBlank(plain)) {
                normalizedParts.add(normalizeText(part));
                continue;
            }

            normalizedParts.add(normalizeSingleWordForLookup(normLang, plain));
        }

        return normalizeText(String.join(" ", normalizedParts));
    }

    private static String normalizeSingleWordForLookup(String lang, String word) {
        String w = cleanGlossaryText(word);
        if (isBlank(w)) {
            return "";
        }
        return RussianGlossaryInflector.normalizeWordForLookup(lang, w);
    }

    private static String removeCommonPossessiveSuffixes(String lang, String w) {
        if ("enus".equals(lang) && w.endsWith("'s") && w.length() > 3) {
            return w.substring(0, w.length() - 2);
        }
        return w;
    }

    private static String normalizePluralLikeForm(String lang, String w) {
        if (w.length() < 4) {
            return w;
        }

        switch (lang) {
            case "enus":
                if (w.endsWith("ies") && w.length() > 4) return w.substring(0, w.length() - 3) + "y";
                if (w.endsWith("es") && w.length() > 4) return w.substring(0, w.length() - 2);
                if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) return w.substring(0, w.length() - 1);
                return w;

            case "dede":
                if (w.endsWith("en") && w.length() > 4) return w.substring(0, w.length() - 2);
                if (w.endsWith("e") && w.length() > 4) return w.substring(0, w.length() - 1);
                if (w.endsWith("er") && w.length() > 4) return w.substring(0, w.length() - 2);
                if (w.endsWith("s") && w.length() > 4) return w.substring(0, w.length() - 1);
                return w;

            case "eses":
            case "esmx":
                if (w.endsWith("es") && w.length() > 4) return w.substring(0, w.length() - 2);
                if (w.endsWith("s") && w.length() > 3) return w.substring(0, w.length() - 1);
                return w;

            case "frfr":
                if (w.endsWith("aux") && w.length() > 5) return w.substring(0, w.length() - 3) + "al";
                if (w.endsWith("eux") && w.length() > 5) return w;
                if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) return w.substring(0, w.length() - 1);
                if (w.endsWith("x") && w.length() > 4) return w.substring(0, w.length() - 1);
                return w;

            case "itit":
                if (w.endsWith("i") && w.length() > 4) return w.substring(0, w.length() - 1) + "o";
                if (w.endsWith("e") && w.length() > 4) return w.substring(0, w.length() - 1) + "a";
                return w;

            case "ptbr":
                if (w.endsWith("Ć„ā€ Äā‚¬Ė›Ć„ĀÄā€Ā¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Āµes") && w.length() > 5) return w.substring(0, w.length() - 3) + "Ć„ā€ Äā‚¬Ė›Ć„ĀÄā€Ā¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā£o";
                if (w.endsWith("ais") && w.length() > 5) return w.substring(0, w.length() - 3) + "al";
                if (w.endsWith("eis") && w.length() > 5) return w.substring(0, w.length() - 3) + "el";
                if (w.endsWith("is") && w.length() > 4) return w.substring(0, w.length() - 2) + "il";
                if (w.endsWith("s") && w.length() > 3) return w.substring(0, w.length() - 1);
                return w;

            case "plpl":
                if (w.endsWith("owie") && w.length() > 6) return w.substring(0, w.length() - 4);
                if (w.endsWith("ami") && w.length() > 5) return w.substring(0, w.length() - 3);
                if (w.endsWith("ach") && w.length() > 5) return w.substring(0, w.length() - 3);
                if (w.endsWith("y") && w.length() > 4) return w.substring(0, w.length() - 1);
                if (w.endsWith("i") && w.length() > 4) return w.substring(0, w.length() - 1);
                return w;

            case "kokr":
            case "zhcn":
            case "zhtw":
                return w;

            case "ruru":
                return w;

            default:
                return w;
        }
    }

    private static String normalizeRussianAdjective(String w) {
        String[] suffixes = {
                "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¹Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†Ćø", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†Ćø", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā",
                "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā€ Ä†Ćø", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā€ Ä†Ćø", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Āµ", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Āµ", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¹Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Āµ", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Āµ",
                "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¹", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¹", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ä¼Ā£Ā¼", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ä¼Ā£Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ä¼Ā£Ā¼", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¹Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¦", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¦",
                "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¹Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¹Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¹", "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¹"
        };

        for (String suffix : suffixes) {
            if (w.length() > suffix.length() + 2 && w.endsWith(suffix)) {
                String stem = w.substring(0, w.length() - suffix.length());

                if (suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā€ Ä†Ćø") || suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Āµ") || suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Āµ")
                        || suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾") || suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā")
                        || suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¹") || suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¦")
                        || suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†Ćø") || suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼")
                        || suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼") || suffix.equals("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¹")) {
                    return stem + "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¹";
                }

                return stem + "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¹Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¹";
            }
        }

        return w;
    }

    private static String normalizeRussianNoun(String w) {
        if (w.length() < 4) {
            return w;
        }

        if (w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†Ćø") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†Ćø")) {
            return w.substring(0, w.length() - 3);
        }
        if (w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā²") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā²") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼")
                || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¦") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¦")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā€ Ä†Ćø") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ä¼Ā£Ā¼")
                || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¹") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†Ćø") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Āµ") || w.endsWith("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾")) {
            return w.substring(0, w.length() - 1);
        }

        return w;
    }

    private static String stripEdgePunctuation(String s) {
        if (s == null || s.isEmpty()) return s;

        int start = 0;
        int end = s.length();

        while (start < end && isEdgePunctuation(s.charAt(start))) {
            start++;
        }
        while (end > start && isEdgePunctuation(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean isEdgePunctuation(char ch) {
        return Character.isWhitespace(ch)
                || ",.;:!?()[]{}<>Ć„ā€ Äā‚¬Ė›Ć„ĀÄā‚¬ĀÄ€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā«Ć„ā€ Äā‚¬Ė›Ć„ĀÄā‚¬ĀÄ€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ė›Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ä†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ć…ā€”Ć„ā€ Äā‚¬Ė›Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ė›Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā€ Äā‚¬Ā '\\\"".indexOf(ch) >= 0;
    }

    private static String stripBom(String s) {
        if (s == null) return null;
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private static final class LookupKey {
        private final Category category;
        private final String key;
        private final String sourceLang;
        private final String sourceText;
        private final String targetLang;

        private LookupKey(Category category,
                          String key,
                          String sourceLang,
                          String sourceText,
                          String targetLang) {
            this.category = category;
            this.key = key;
            this.sourceLang = sourceLang;
            this.sourceText = sourceText;
            this.targetLang = targetLang;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LookupKey)) return false;
            LookupKey that = (LookupKey) o;
            return category == that.category
                    && Objects.equals(key, that.key)
                    && Objects.equals(sourceLang, that.sourceLang)
                    && Objects.equals(sourceText, that.sourceText)
                    && Objects.equals(targetLang, that.targetLang);
        }

        @Override
        public int hashCode() {
            return Objects.hash(category, key, sourceLang, sourceText, targetLang);
        }
    }
    private static final class TextOnlyLookupKey {
        private final Category category;
        private final String sourceLang;
        private final String sourceText;
        private final String targetLang;

        private TextOnlyLookupKey(Category category,
                                  String sourceLang,
                                  String sourceText,
                                  String targetLang) {
            this.category = category;
            this.sourceLang = sourceLang;
            this.sourceText = sourceText;
            this.targetLang = targetLang;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TextOnlyLookupKey)) return false;
            TextOnlyLookupKey that = (TextOnlyLookupKey) o;
            return category == that.category
                    && Objects.equals(sourceLang, that.sourceLang)
                    && Objects.equals(sourceText, that.sourceText)
                    && Objects.equals(targetLang, that.targetLang);
        }

        @Override
        public int hashCode() {
            return Objects.hash(category, sourceLang, sourceText, targetLang);
        }
    }
    public ReadOnlyBooleanProperty glossaryLoadingProperty() {
        return glossaryLoading;
    }

    public ReadOnlyBooleanProperty glossaryReadyProperty() {
        return glossaryReady;
    }

    public ReadOnlyStringProperty glossaryStatusProperty() {
        return glossaryStatus;
    }

    public boolean isGlossaryReady() {
        return glossaryReady.get();
    }
    public void loadGlossariesAsync(List<Path> files) {
        glossaryLoading.set(true);
        glossaryReady.set(false);
        glossaryStatus.set("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ĀÄā€Ā¬Ä€Ć†Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā·Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā° Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā²...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                clear();

                for (Path file : files) {
                    String fileName = file.getFileName().toString();

                    try (InputStream in = Files.newInputStream(file)) {
                        if (isTxtGlossaryFile(fileName)) {
                            if (isWordGlossaryFile(fileName)) {
                                loadWordGlossaryFromStream(in, fileName);
                            } else {
                                loadTxtGlossaryFromStream(in, fileName);
                            }
                        } else if (fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                            Category category = detectCategoryByFileName(fileName);
                            if (category != null) {
                                loadFromStream(in, category, fileName);
                            }
                        }
                    }
                }

                //    buildAutoTerms();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            glossaryLoading.set(false);
            glossaryReady.set(true);
            glossaryStatus.set("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†Ćø Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā·Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¶Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā½Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¹");
            AppLog.info("[Glossary] success, loading=" + glossaryLoading.get() + ", ready=" + glossaryReady.get());
        });

        task.setOnFailed(e -> {
            glossaryLoading.set(false);
            glossaryReady.set(false);
            glossaryStatus.set("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ć…ā€”Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā±Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā° Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā·Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā·Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†Ćø Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā²");
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(task, "glossary-loader");
        thread.setDaemon(true);
        thread.start();
    }
    private boolean isTxtGlossaryFile(String fileName) {
        if (fileName == null) return false;
        String s = fileName.trim().toLowerCase(Locale.ROOT);
        return s.endsWith(".txt") && s.contains("glossary");
    }
    private boolean isWordGlossaryFile(String fileName) {
        if (fileName == null) return false;
        String s = fileName.trim().toLowerCase(Locale.ROOT);
        return s.endsWith(".txt") && s.contains("word_glossary");
    }

    private void loadTxtGlossaryFromStream(InputStream is, String sourceName) throws IOException {
        List<String[]> rows = readSemicolonCsv(is);
        if (rows.isEmpty()) {
            AppLog.warn("[Glossary/TXT] Empty TXT: " + sourceName);
            return;
        }

        int imported = 0;
        List<String> langs = new ArrayList<>(LANGS);

        for (String[] row : rows) {
            if (isRowBlank(row)) {
                continue;
            }

            Map<String, String> textsByLang = new HashMap<>();
            for (int i = 0; i < langs.size() && i < row.length; i++) {
                String txt = cleanGlossaryText(getCell(row, i));
                if (!isBlank(txt)) {
                    textsByLang.put(langs.get(i), txt.trim());
                }
            }

            if (textsByLang.isEmpty()) {
                continue;
            }

            for (String sourceLang : langs) {
                String sourceText = textsByLang.get(sourceLang);
                if (isBlank(sourceText)) {
                    continue;
                }

                for (String targetLang : langs) {
                    if (targetLang.equals(sourceLang)) {
                        continue;
                    }

                    String targetText = textsByLang.get(targetLang);
                    if (isBlank(targetText)) {
                        continue;
                    }

                    TxtLookupKey key = new TxtLookupKey(
                            normalizeLang(sourceLang),
                            normalizeText(sourceText),
                            normalizeLang(targetLang)
                    );
                    txtTextOnlyMap.putIfAbsent(key, targetText.trim());
                    imported++;
                }
            }
        }

        AppLog.info("[Glossary/TXT] Loaded " + imported + " mappings from " + sourceName
                + " | txt keys = " + txtTextOnlyMap.size());
    }

    private void loadWordGlossaryFromStream(InputStream is, String sourceName) throws IOException {
        List<String[]> rows = readSemicolonCsv(is);
        if (rows.isEmpty()) {
            AppLog.warn("[Glossary/WORD] Empty TXT: " + sourceName);
            return;
        }

        int imported = 0;
        int normalizedImported = 0;
        List<String> langs = new ArrayList<>(LANGS);

        for (String[] row : rows) {
            if (isRowBlank(row)) {
                continue;
            }

            Map<String, String> textsByLang = new HashMap<>();
            for (int i = 0; i < langs.size() && i < row.length; i++) {
                String txt = cleanGlossaryText(getCell(row, i));
                if (!isBlank(txt)) {
                    textsByLang.put(langs.get(i), txt.trim());
                }
            }

            if (textsByLang.isEmpty()) {
                continue;
            }

            for (String sourceLang : langs) {
                String sourceText = textsByLang.get(sourceLang);
                if (isBlank(sourceText) || !looksLikeReusableWord(sourceText)) {
                    continue;
                }

                for (String targetLang : langs) {
                    if (targetLang.equals(sourceLang)) {
                        continue;
                    }

                    String targetText = textsByLang.get(targetLang);
                    if (isBlank(targetText) || !looksLikeReusableWord(targetText)) {
                        continue;
                    }

                    TxtLookupKey directKey = new TxtLookupKey(
                            normalizeLang(sourceLang),
                            normalizeText(sourceText),
                            normalizeLang(targetLang)
                    );
                    wordTextOnlyMap.putIfAbsent(directKey, targetText.trim());
                    imported++;

                    String normalizedSource = normalizeSourceTermForLookup(sourceLang, sourceText);
                    if (!isBlank(normalizedSource)) {
                        TxtLookupKey normalizedKey = new TxtLookupKey(
                                normalizeLang(sourceLang),
                                normalizedSource,
                                normalizeLang(targetLang)
                        );
                        normalizedWordTextOnlyMap.putIfAbsent(normalizedKey, targetText.trim());
                        normalizedImported++;
                    }
                }
            }
        }

        AppLog.info("[Glossary/WORD] Loaded " + imported + " direct mappings from " + sourceName
                + " | direct keys = " + wordTextOnlyMap.size());
        AppLog.info("[Glossary/WORD] Loaded " + normalizedImported + " normalized mappings from " + sourceName
                + " | normalized keys = " + normalizedWordTextOnlyMap.size());
    }

    private boolean looksLikeReusableWord(String s) {
        String cleaned = cleanGlossaryText(s);
        if (isBlank(cleaned)) return false;
        String[] words = cleaned.split("\\s+");
        if (words.length < 1 || words.length > 3) {
            return false;
        }
        return true;
    }

    private List<String> buildLookupCandidates(String sourceLang, String sourceText) {
        LinkedHashSet<String> out = new LinkedHashSet<>();

        String cleaned = cleanGlossaryText(sourceText);
        if (isBlank(cleaned)) {
            return new ArrayList<>();
        }

        String lang = normalizeLang(sourceLang);
        String direct = normalizeText(cleaned).replace("\u0451", "\u0435");
        if (!isBlank(direct)) {
            out.add(direct);
        }

        String normalized = normalizeSourceTermForLookup(lang, cleaned);
        if (!isBlank(normalized)) {
            out.add(normalized.replace("\u0451", "\u0435"));
        }

        String plain = stripEdgePunctuation(cleaned);
        if (!isBlank(plain)) {
            String lower = normalizeText(plain).replace("\u0451", "\u0435");
            if (!isBlank(lower)) {
                out.add(lower);
            }

            if ("ruru".equals(lang)) {
                String noun = RussianGlossaryInflector.normalizeRussianNounForLookup(lower);
                if (!isBlank(noun)) {
                    out.add(noun);
                }
                String adj = RussianGlossaryInflector.normalizeRussianAdjectiveForLookup(lower);
                if (!isBlank(adj)) {
                    out.add(adj);
                }
            }
        }

        return new ArrayList<>(out);
    }

    private static String applySourceCaseStyle(String source, String translated) {
        if (isBlank(source) || isBlank(translated)) {
            return translated;
        }

        String plain = stripEdgePunctuation(source);
        if (isBlank(plain)) {
            return translated;
        }

        if (plain.equals(plain.toUpperCase(Locale.ROOT))) {
            return translated.toUpperCase(Locale.ROOT);
        }

        if (Character.isUpperCase(plain.codePointAt(0))) {
            if (translated.length() == 1) {
                return translated.toUpperCase(Locale.ROOT);
            }
            return translated.substring(0, 1).toUpperCase(Locale.ROOT) + translated.substring(1);
        }

        return translated;
    }

    public String findWordMatch(String sourceLang, String sourceText, String targetLang) {
        if (isBlank(sourceLang) || isBlank(sourceText) || isBlank(targetLang)) {
            return null;
        }

        String cleaned = cleanGlossaryText(sourceText);
        if (isBlank(cleaned)) {
            return null;
        }

        String src = normalizeLang(sourceLang);
        String trg = normalizeLang(targetLang);

        for (String candidate : buildLookupCandidates(src, cleaned)) {
            TxtLookupKey key = new TxtLookupKey(src, candidate, trg);

            String hit = wordTextOnlyMap.get(key);
            if (!isBlank(hit)) {
                return applySourceCaseStyle(cleaned, hit);
            }

            hit = normalizedWordTextOnlyMap.get(key);
            if (!isBlank(hit)) {
                return applySourceCaseStyle(cleaned, hit);
            }
        }

        return null;
    }

    // Return composed translation only if all meaningful tokens were translated.
// If at least one token is missing, return null so MT fallback can handle the whole text.
    public String composeFromWordGlossary(String sourceLang, String sourceText, String targetLang) {
        if (isBlank(sourceLang) || isBlank(sourceText) || isBlank(targetLang)) {
            return null;
        }

        String cleaned = cleanGlossaryText(sourceText);
        if (isBlank(cleaned)) return null;

        String lookupText = stripMarkupTags(cleaned).trim();
        if (isBlank(lookupText)) return null;

        if (normalizeLang(sourceLang).equals(normalizeLang(targetLang))) {
            return cleaned;
        }

        if (!cleaned.matches(".*(\\s|[()_/-]).*")) {
            return null;
        }

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\\s+|[^\\s]+").matcher(cleaned);

        StringBuilder out = new StringBuilder();
        int translatedCount = 0;
        while (matcher.find()) {
            String token = matcher.group();

            if (token.isBlank()) {
                out.append(token);
                continue;
            }

            String plain = token.replaceAll("^[\\p{Punct}Ć„ā€ Äā‚¬Ė›Ć„ĀÄā‚¬ĀÄ€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā«Ć„ā€ Äā‚¬Ė›Ć„ĀÄā‚¬ĀÄ€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ė›Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ä†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ć…ā€”Ć„ā€ Äā‚¬Ė›Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ė›Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā€ Äā‚¬Ā ]+|[\\p{Punct}Ć„ā€ Äā‚¬Ė›Ć„ĀÄā‚¬ĀÄ€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā«Ć„ā€ Äā‚¬Ė›Ć„ĀÄā‚¬ĀÄ€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ė›Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ä†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ć…ā€”Ć„ā€ Äā‚¬Ė›Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ė›Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā€ Äā‚¬Ā ]+$", "");
            if (isBlank(plain) || !looksLikeReusableWord(plain)) {
                out.append(token);
                continue;
            }

            String translated = findWordMatch(sourceLang, plain, targetLang);
            if (!isBlank(translated) && !translated.equalsIgnoreCase(plain)) {
                out.append(token.replace(plain, translated));
                translatedCount++;
            } else {
                out.append(token);
            }
        }

        String result = out.toString().trim();

        if (translatedCount == 0) {
            return null;
        }

        // Allow partial composition.
        // It is still better than losing glossary terms like Cancel, Roach, Mutalisk.
        return result.equals(cleaned) ? null : result;
    }

    /**
     * Compose phrase with MT fallback for untranslated words.
     * 1. Try word-by-word glossary translation
     * 2. For untranslated words, send them individually to MT
     * 3. Return fully translated phrase
     * 
     * Example: "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ć…ā€”Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā½Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā½ Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā·Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā½Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°" (ru) -> "en"
     * - "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ć…ā€”Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¼Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā½Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā" -> "Cancel" (glossary)
     * - "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā½" -> MT translation (not in glossary)
     * - "Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā·Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā½Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°" -> "zergling" (glossary)
     * Result: "Cancel [mt_translation_of_Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā½] zergling"
     */
    public String composePhraseWithMtFallback(String sourceLang, String sourceText, String targetLang) {
        if (isBlank(sourceLang) || isBlank(sourceText) || isBlank(targetLang)) {
            return null;
        }

        String cleaned = cleanGlossaryText(sourceText);
        if (isBlank(cleaned)) return null;

        if (normalizeLang(sourceLang).equals(normalizeLang(targetLang))) {
            return cleaned;
        }

        // Single word - try glossary first, then MT
        if (!cleaned.matches(".*(\\s|[()_/-]).*")) {
            String wordMatch = findWordMatch(sourceLang, cleaned, targetLang);
            if (!isBlank(wordMatch)) {
                return wordMatch;
            }
            // Single word not found - try MT
            return translateViaMt(cleaned, sourceLang, targetLang);
        }

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\\s+|[^\\s]+").matcher(cleaned);

        StringBuilder out = new StringBuilder();
        java.util.List<String> untranslatedWords = new java.util.ArrayList<>();
        java.util.Map<String, String> untranslatedToTranslated = new java.util.LinkedHashMap<>();
        int translatedCount = 0;

        while (matcher.find()) {
            String token = matcher.group();

            if (token.isBlank()) {
                out.append(token);
                continue;
            }

            String plain = token.replaceAll("^\\p{Punct}+|\\p{Punct}+$", "");
            if (isBlank(plain) || !looksLikeReusableWord(plain)) {
                out.append(token);
                continue;
            }

            String translated = findWordMatch(sourceLang, plain, targetLang);
            if (!isBlank(translated) && !translated.equalsIgnoreCase(plain)) {
                out.append(token.replace(plain, translated));
                translatedCount++;
            } else {
                // Word not found in glossary - mark for MT translation
                untranslatedWords.add(plain);
                out.append("__UNTRANS__").append(untranslatedWords.size() - 1).append("__");
            }
        }

        // If there are untranslated words, translate them via MT
        if (!untranslatedWords.isEmpty()) {
            try {
                java.util.List<String> mtTranslations = TranslationService.translatePreservingTags(
                        TranslationService.api,
                        untranslatedWords,
                        toLibreCode(sourceLang),
                        toLibreCode(targetLang)
                );

                if (mtTranslations != null && !mtTranslations.isEmpty()) {
                    for (int i = 0; i < untranslatedWords.size(); i++) {
                        String original = untranslatedWords.get(i);
                        String translated = (i < mtTranslations.size()) ? mtTranslations.get(i) : null;
                        if (!isBlank(translated)) {
                            untranslatedToTranslated.put(original, translated);
                        }
                    }
                }
            } catch (Exception e) {
                AppLog.warn("[GlossaryService] MT translation failed for untranslated words: " + e.getMessage());
            }

            // Replace placeholders with MT translations
            String result = out.toString();
            for (int i = 0; i < untranslatedWords.size(); i++) {
                String placeholder = "__UNTRANS__" + i + "__";
                String original = untranslatedWords.get(i);
                String translated = untranslatedToTranslated.getOrDefault(original, original);
                result = result.replace(placeholder, translated);
            }

            return result.trim();
        }

        String result = out.toString().trim();
        if (translatedCount == 0) {
            return null;
        }

        return result.equals(cleaned) ? null : result;
    }
    private static String stripMarkupTags(String s) {
        if (s == null) return null;
        return s.replaceAll("<[^>]+>", "");
    }
    public String findTxtMatch(String sourceLang, String sourceText, String targetLang) {
        if (isBlank(sourceLang) || isBlank(sourceText) || isBlank(targetLang)) {
            return null;
        }

        TxtLookupKey key = new TxtLookupKey(
                normalizeLang(sourceLang),
                normalizeText(sourceText),
                normalizeLang(targetLang)
        );

        String hit = txtTextOnlyMap.get(key);
        return isBlank(hit) ? null : hit;
    }
    public void clear() {
        exactMap.clear();
        textOnlyMap.clear();
        txtTextOnlyMap.clear();
        wordTextOnlyMap.clear();
        normalizedWordTextOnlyMap.clear();
        wordSources.clear();
        rows.clear();
        termIndex.clear();
    }
    private Category detectCategoryByFileName(String fileName) {
        if (fileName == null) return null;

        String s = fileName.trim().toLowerCase(Locale.ROOT);

        if (s.contains("unit")) {
            return Category.UNIT;
        }
        if (s.contains("button")) {
            return Category.BUTTON;
        }
        if (s.contains("abil") || s.contains("ability")) {
            return Category.ABILITY;
        }

        return null;
    }
    public void loadGlossariesAsyncFromResources() {
        glossaryLoading.set(true);
        glossaryReady.set(false);
        glossaryStatus.set("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ĀÄā€Ā¬Ä€Ć†Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā·Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā° Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā²...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                clear();
                ensureEditableGlossariesAvailableExternally();
                if (SettingsManager.loadCheckboxState(SettingsManager.BASE_GLOSSARY_KEY, SettingsManager.DEFAULT_BASE_GLOSSARY)) {
                    if (!loadTxtGlossaryFromExternalIfPresent(WORD_GLOSSARY_FILE)) {
                        loadTxtFromResource("/glossary/" + WORD_GLOSSARY_FILE);
                    }
                }
                if (SettingsManager.loadCheckboxState(SettingsManager.UNITS_GLOSSARY_KEY, SettingsManager.DEFAULT_UNITS_GLOSSARY)) {
                    loadAdditionalGlossaryFile("Addition_UnitNames_Detailed_KSP.txt");
                }
                if (SettingsManager.loadCheckboxState(SettingsManager.WEAPONS_GLOSSARY_KEY, SettingsManager.DEFAULT_WEAPONS_GLOSSARY)) {
                    loadAdditionalGlossaryFile("Addition_Weapons_Detailed_KSP.txt");
                }
                if (SettingsManager.loadCheckboxState(SettingsManager.ABILITIES_GLOSSARY_KEY, SettingsManager.DEFAULT_ABILITIES_GLOSSARY)) {
                    loadAdditionalGlossaryFile("Addition_Abilities_Detailed_KSP.txt");
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            glossaryLoading.set(false);
            glossaryReady.set(true);
            glossaryStatus.set("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†Ćø Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā·Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¶Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā½Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā‚¬ĀÄ€Ā¬Ć„ā‚¬Ä€Ā¹");
        });

        task.setOnFailed(e -> {
            glossaryLoading.set(false);
            glossaryReady.set(false);
            glossaryStatus.set("Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬ā€Ć„ĀÄā€Ā¬Ć…ā€”Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā±Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā° Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā·Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā·Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ć„ā€ Äā‚¬Ā¦Ć„ĀÄā€Ā¬Äā‚¬ĀÆĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†Ćø Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā³Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā¾Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā°Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€ĀÄ†ā€˛Ä€ĀĆ„ĀÄā€Ā¬Ä€ĀĆ„ā‚¬Ä€Ā¬Ć„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā‚¬Ā Ć„ā€ Ä†ĆøĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€ĀµĆ„ā€ Äā‚¬Ā¦Ć„ā‚¬Ä€Ā Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā²");
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(task, "glossary-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadAdditionalGlossaryFile(String fileName) {
        if (!loadTxtGlossaryFromExternalIfPresent(fileName)) {
            loadTxtFromResource("/glossary/" + fileName);
        }
    }

    private boolean loadTxtGlossaryFromExternalIfPresent(String fileName) {
        Path dir = resolveExternalGlossaryDir();
        if (dir == null || fileName == null || fileName.isBlank()) {
            return false;
        }

        Path file = dir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return false;
        }

        try (InputStream is = new FileInputStream(file.toFile())) {
            if (isWordGlossaryFile(fileName)) {
                loadWordGlossaryFromStream(is, file.toAbsolutePath().toString());
            } else {
                loadTxtGlossaryFromStream(is, file.toAbsolutePath().toString());
            }
            AppLog.info("[Glossary] Loaded external glossary: " + file.toAbsolutePath());
            return true;
        } catch (Exception ex) {
            AppLog.warn("[Glossary] Failed to load external glossary " + file.toAbsolutePath() + ": " + ex.getMessage());
            return false;
        }
    }

    private void ensureEditableGlossariesAvailableExternally() {
        Path dir = resolvePreferredExternalGlossaryDir();
        if (dir == null) {
            return;
        }

        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            AppLog.warn("[Glossary] Failed to create editable glossary dir " + dir.toAbsolutePath() + ": " + ex.getMessage());
            return;
        }

        for (String fileName : EDITABLE_GLOSSARY_FILES) {
            Path target = dir.resolve(fileName);
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream is = GlossaryService.class.getResourceAsStream("/glossary/" + fileName)) {
                if (is == null) {
                    AppLog.warn("[Glossary] Editable glossary resource not found: " + fileName);
                    continue;
                }
                Files.copy(is, target);
                AppLog.info("[Glossary] Seeded editable glossary: " + target.toAbsolutePath());
            } catch (Exception ex) {
                AppLog.warn("[Glossary] Failed to seed editable glossary " + target.toAbsolutePath() + ": " + ex.getMessage());
            }
        }
    }

    private static Path resolvePreferredExternalGlossaryDir() {
        Path configuredDir = resolveConfiguredGlossaryDir();
        if (configuredDir != null) {
            return configuredDir;
        }

        Path installedGlossaryDir = firstExistingDirectory(resolveAppGlossaryCandidates());
        if (installedGlossaryDir != null) {
            return installedGlossaryDir;
        }

        return resolveLegacyGlossaryDir();
    }

    private static Path resolveExternalGlossaryDir() {
        Path configuredDir = resolveConfiguredGlossaryDir();
        if (configuredDir != null && Files.isDirectory(configuredDir)) {
            return configuredDir;
        }

        Path installedGlossaryDir = firstExistingDirectory(resolveAppGlossaryCandidates());
        if (installedGlossaryDir != null) {
            return installedGlossaryDir;
        }

        Path legacyDir = resolveLegacyGlossaryDir();
        if (legacyDir != null && Files.isDirectory(legacyDir)) {
            return legacyDir;
        }
        return null;
    }

    private static Path resolveConfiguredGlossaryDir() {
        String configured = System.getProperty("glossary.dir");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        return null;
    }

    private static Path resolveLegacyGlossaryDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "Localization Editor SC2 KSP", "glossary");
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            return Path.of(userHome, ".Localization_Editor_SC2_KSP", "glossary");
        }
        return null;
    }

    private static List<Path> resolveAppGlossaryCandidates() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();

        try {
            Path appLocation = Path.of(Objects.requireNonNull(
                    GlossaryService.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
            Path appDir = Files.isDirectory(appLocation) ? appLocation : appLocation.getParent();
            if (appDir != null) {
                Path installRoot = appDir.getParent();
                if (installRoot != null) {
                    // jpackage layout: <install>\app\*.jar, so editable files should live in <install>\glossary
                    candidates.add(installRoot.resolve("glossary"));
                }
                candidates.add(appDir.resolve("glossary"));
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // fallback below
        }

        candidates.add(Path.of(System.getProperty("user.dir", "."), "glossary"));
        return new ArrayList<>(candidates);
    }

    private static Path firstExistingDirectory(List<Path> candidates) {
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static final class TxtLookupKey {
        final String sourceLang;
        final String normalizedSource;
        final String targetLang;

        TxtLookupKey(String sourceLang, String normalizedSource, String targetLang) {
            this.sourceLang = sourceLang;
            this.normalizedSource = normalizedSource;
            this.targetLang = targetLang;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TxtLookupKey)) return false;
            TxtLookupKey that = (TxtLookupKey) o;
            return Objects.equals(sourceLang, that.sourceLang)
                    && Objects.equals(normalizedSource, that.normalizedSource)
                    && Objects.equals(targetLang, that.targetLang);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceLang, normalizedSource, targetLang);
        }
    }

    private static final class GlossaryRow {
        final Category category;
        final String key;
        final Map<String, String> textsByLang;

        GlossaryRow(Category category, String key, Map<String, String> textsByLang) {
            this.category = category;
            this.key = key;
            this.textsByLang = textsByLang;
        }
    }
    private static final class TermLookupKey {
        final Category category;
        final String sourceLang;
        final String targetLang;

        TermLookupKey(Category category, String sourceLang, String targetLang) {
            this.category = category;
            this.sourceLang = sourceLang;
            this.targetLang = targetLang;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TermLookupKey)) return false;
            TermLookupKey that = (TermLookupKey) o;
            return category == that.category
                    && Objects.equals(sourceLang, that.sourceLang)
                    && Objects.equals(targetLang, that.targetLang);
        }

        @Override
        public int hashCode() {
            return Objects.hash(category, sourceLang, targetLang);
        }
    }

    private static final class TermEntry {
        final String sourceText;
        final String targetText;
        final int priority;

        TermEntry(String sourceText, String targetText, int priority) {
            this.sourceText = sourceText;
            this.targetText = targetText;
            this.priority = priority;
        }
    }

    private static final class TermCandidate {
        final Category category;
        final String sourceLang;
        final String targetLang;
        final String sourceText;
        final String targetText;
        final String key;

        TermCandidate(Category category,
                      String sourceLang,
                      String targetLang,
                      String sourceText,
                      String targetText,
                      String key) {
            this.category = category;
            this.sourceLang = sourceLang;
            this.targetLang = targetLang;
            this.sourceText = sourceText;
            this.targetText = targetText;
            this.key = key;
        }
    }
    @SuppressWarnings("unused")
    private void buildAutoTerms() {
        termIndex.clear();

        for (GlossaryRow row : rows) {
            for (String sourceLang : LANGS) {
                String src = row.textsByLang.get(sourceLang);
                if (isBlank(src)) continue;

                for (String targetLang : LANGS) {
                    if (targetLang.equals(sourceLang)) continue;

                    String trg = row.textsByLang.get(targetLang);
                    if (isBlank(trg)) continue;

                    TermCandidate c = new TermCandidate(
                            row.category,
                            sourceLang,
                            targetLang,
                            src.trim(),
                            trg.trim(),
                            row.key
                    );

                    if (!looksLikeSimpleReusableTerm(c)) continue;

                    TermLookupKey key = new TermLookupKey(c.category, c.sourceLang, c.targetLang);
                    termIndex.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(new TermEntry(
                                    c.sourceText,
                                    c.targetText,
                                    calcPriority(c)
                            ));
                }
            }
        }

        for (List<TermEntry> list : termIndex.values()) {
            list.sort(Comparator
                    .comparingInt((TermEntry t) -> t.sourceText.length()).reversed()
                    .thenComparingInt((TermEntry t) -> t.priority).reversed());
        }

        AppLog.info("[Glossary] Auto terms built: " + termIndex.size() + " buckets");
    }
    private boolean looksLikeSimpleReusableTerm(TermCandidate c) {
        String src = cleanGlossaryText(c.sourceText);
        String trg = cleanGlossaryText(c.targetText);
        if (isBlank(src) || isBlank(trg)) return false;

        if (src.length() < 3 || trg.length() < 3) return false;
        if (src.length() > 48 || trg.length() > 48) return false;

        String ns = normalizeText(src);
        if (ns.contains("->")) return false;
        if (ns.contains("///")) return false;

        int wordCount = src.trim().split("\\s+").length;
        if (wordCount < 1 || wordCount > 4) return false;

        String keyNorm = normalizeKey(c.key);

        if (keyNorm.contains("/tooltip/")
                || keyNorm.contains("/desc/")
                || keyNorm.contains("/description/")
                || keyNorm.contains("/hint/")) {
            return false;
        }

        return true;
    }
    private int calcPriority(TermCandidate c) {
        int score = 0;

        String src = cleanGlossaryText(c.sourceText);
        if (isBlank(src)) return score;

        int words = src.trim().split("\\s+").length;

        if (c.category == Category.UNIT) {
            score += 100;
        }

        if (words >= 2) {
            score += 20;
        }

        score += Math.min(src.length(), 50);

        return score;
    }

    public static final class FrozenTerms {
        private final String preparedText;
        private final Map<String, String> tokenToTarget;

        public FrozenTerms(String preparedText, Map<String, String> tokenToTarget) {
            this.preparedText = preparedText;
            this.tokenToTarget = tokenToTarget;
        }

        public String preparedText() {
            return preparedText;
        }

        public Map<String, String> tokenToTarget() {
            return tokenToTarget;
        }
    }
    public FrozenTerms freezeTerms(Category category,
                                   String sourceLang,
                                   String targetLang,
                                   String text) {
        if (isBlank(sourceLang) || isBlank(targetLang) || isBlank(text)) {
            return new FrozenTerms(text, Collections.emptyMap());
        }

        String out = text;
        Map<String, String> tokenToTarget = new LinkedHashMap<>();
        int counter = 0;

        if (category != null) {
            TermLookupKey key = new TermLookupKey(
                    category,
                    normalizeLang(sourceLang),
                    normalizeLang(targetLang)
            );

            List<TermEntry> terms = termIndex.getOrDefault(key, Collections.emptyList());
            for (TermEntry term : terms) {
                String token = "__SC2_TERM_" + counter + "__";
                String replaced = replaceWholeWordUnicode(out, term.sourceText, token);

                if (!replaced.equals(out)) {
                    out = replaced;
                    tokenToTarget.put(token, term.targetText);
                    counter++;
                }
            }
        }

        // Also freeze individual words from word glossary:
        // e.g. "zergling tuffta" -> "zergling" frozen from glossary, "tuffta" stays for MT.
        FreezeWordResult wordsResult = freezeWordsByGlossary(sourceLang, targetLang, out, tokenToTarget, counter);
        out = wordsResult.text();
        counter = wordsResult.nextCounter();

        return new FrozenTerms(out, tokenToTarget);
    }

    public Map<String, String> collectTermHints(Category category,
                                                String sourceLang,
                                                String targetLang,
                                                String text,
                                                int limit) {
        if (limit <= 0 || isBlank(sourceLang) || isBlank(targetLang) || isBlank(text)) {
            return Collections.emptyMap();
        }

        LinkedHashMap<String, String> hints = new LinkedHashMap<>();
        String src = normalizeLang(sourceLang);
        String trg = normalizeLang(targetLang);

        List<TermLookupKey> buckets = new ArrayList<>();
        if (category != null) {
            buckets.add(new TermLookupKey(category, src, trg));
        } else {
            buckets.add(new TermLookupKey(Category.UNIT, src, trg));
            buckets.add(new TermLookupKey(Category.BUTTON, src, trg));
            buckets.add(new TermLookupKey(Category.ABILITY, src, trg));
        }

        for (TermLookupKey key : buckets) {
            List<TermEntry> terms = termIndex.getOrDefault(key, Collections.emptyList());
            for (TermEntry term : terms) {
                if (hints.size() >= limit) {
                    return hints;
                }
                if (isBlank(term.sourceText) || isBlank(term.targetText)) {
                    continue;
                }
                if (!containsWholeWordUnicode(text, term.sourceText)) {
                    continue;
                }
                hints.putIfAbsent(term.sourceText, term.targetText);
            }
        }

        appendWordGlossaryHintsFromText(hints, sourceLang, targetLang, text, limit);
        return hints;
    }

    private void appendWordGlossaryHintsFromText(Map<String, String> out,
                                                 String sourceLang,
                                                 String targetLang,
                                                 String text,
                                                 int limit) {
        if (out == null || limit <= 0 || isBlank(text)) {
            return;
        }

        List<String> words = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            if (!isBlank(word)) {
                words.add(word);
            }
        }

        // Detect glossary hits directly from text tokens.
        for (String word : words) {
            if (out.size() >= limit) {
                return;
            }
            String hit = findWordMatch(sourceLang, word, targetLang);
            if (!isBlank(hit)) {
                out.putIfAbsent(word, hit);
            }
        }

        // Also check short n-grams (2-3 words), because many SC2 terms are multi-word.
        int n = words.size();
        for (int size = 3; size >= 2; size--) {
            if (out.size() >= limit) {
                return;
            }
            if (n < size) {
                continue;
            }
            for (int i = 0; i <= n - size; i++) {
                if (out.size() >= limit) {
                    return;
                }
                String phrase = String.join(" ", words.subList(i, i + size));
                String hit = findWordMatch(sourceLang, phrase, targetLang);
                if (!isBlank(hit)) {
                    out.putIfAbsent(phrase, hit);
                }
            }
        }
    }

    public String unfreezeTerms(String text, FrozenTerms frozen) {
        return unfreezeTerms(text, frozen, null);
    }

    public String unfreezeTerms(String text, FrozenTerms frozen, String targetLang) {
        if (isBlank(text)) {
            if (frozen != null && frozen.preparedText() != null && !frozen.preparedText().isBlank()) {
                text = frozen.preparedText();
            } else {
                return text;
            }
        }

        String out = text;
        if (frozen != null && !frozen.tokenToTarget().isEmpty()) {
            out = recoverBrokenGlossaryTokens(out, frozen);
            out = restoreTokenSpacingFromTemplate(out, frozen);

            boolean useRussianInflection = "ruru".equals(normalizeLang(targetLang));
            for (Map.Entry<String, String> e : frozen.tokenToTarget().entrySet()) {
                out = replaceGlossaryToken(out, e.getKey(), e.getValue(), useRussianInflection);
            }
        }
        return out;
    }

    public List<String> unfreezeTermsBatch(List<String> texts,
                                           List<FrozenTerms> frozens,
                                           String targetLang) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        int n = texts.size();
        List<String> stage = new ArrayList<>(n);
        List<Map<String, String>> tokenMaps = new ArrayList<>(n);
        List<Integer> tokenIndexes = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            String text = texts.get(i);
            FrozenTerms frozen = (frozens != null && i < frozens.size()) ? frozens.get(i) : null;
            String prepared = text;
            Map<String, String> map = Collections.emptyMap();
            if (frozen != null && frozen.tokenToTarget() != null && !frozen.tokenToTarget().isEmpty()) {
                prepared = recoverBrokenGlossaryTokens(prepared, frozen);
                prepared = restoreTokenSpacingFromTemplate(prepared, frozen);
                map = frozen.tokenToTarget();
                tokenIndexes.add(i);
            }
            stage.add(prepared);
            tokenMaps.add(map);
        }

        TranslationService.TranslationBackend backend = TranslationService.getSelectedBackend();
        boolean allowAiPlaceholderInflection = TranslationService.backendSupportsGlossaryInflectionHints()
                && shouldUseAiPlaceholderInflection(backend, stage, tokenIndexes);
        if (allowAiPlaceholderInflection && !tokenIndexes.isEmpty()) {
            List<String> aiInputTexts = new ArrayList<>(tokenIndexes.size());
            List<Map<String, String>> aiInputMaps = new ArrayList<>(tokenIndexes.size());
            for (Integer idx : tokenIndexes) {
                aiInputTexts.add(stage.get(idx));
                aiInputMaps.add(tokenMaps.get(idx));
            }

            List<String> aiResolved = tryResolvePlaceholdersWithAi(aiInputTexts, aiInputMaps, targetLang);
            if (aiResolved != null && aiResolved.size() == aiInputTexts.size()) {
                List<String> merged = new ArrayList<>(stage);
                boolean allAccepted = true;
                for (int k = 0; k < tokenIndexes.size(); k++) {
                    int idx = tokenIndexes.get(k);
                    String candidate = aiResolved.get(k);
                    String original = stage.get(idx);
                    if (isAcceptableAiUnfreeze(candidate, original, tokenMaps.get(idx))) {
                        merged.set(idx, candidate);
                    } else {
                        allAccepted = false;
                        break;
                    }
                }
                if (allAccepted) {
                    return merged;
                }
            }
        }

        List<String> out = new ArrayList<>(n);
        boolean useRussianInflection = "ruru".equals(normalizeLang(targetLang));
        for (int i = 0; i < n; i++) {
            String value = stage.get(i);
            Map<String, String> map = tokenMaps.get(i);
            if (map == null || map.isEmpty()) {
                out.add(value);
                continue;
            }

            // Some providers can return blank for token-only text.
            // Fall back to the original prepared text so tokens can still be restored.
            if (isBlank(value)) {
                FrozenTerms frozen = (frozens != null && i < frozens.size()) ? frozens.get(i) : null;
                if (frozen != null && !isBlank(frozen.preparedText())) {
                    value = frozen.preparedText();
                    value = recoverBrokenGlossaryTokens(value, frozen);
                    value = restoreTokenSpacingFromTemplate(value, frozen);
                }
            }

            String resolved = value;
            for (Map.Entry<String, String> e : map.entrySet()) {
                resolved = replaceGlossaryToken(resolved, e.getKey(), e.getValue(), useRussianInflection);
            }
            out.add(resolved);
        }
        return out;
    }

    private static boolean isAcceptableAiUnfreeze(String candidate,
                                                  String originalWithTokens,
                                                  Map<String, String> tokenMap) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        if (tokenMap == null || tokenMap.isEmpty()) {
            return true;
        }
        for (String token : tokenMap.keySet()) {
            if (token == null || token.isBlank()) continue;
            if (candidate.contains(token)) {
                return false;
            }
        }
        String normalizedOriginal = normalizeWhitespaceForCheck(originalWithTokens);
        String normalizedCandidate = normalizeWhitespaceForCheck(candidate);
        if (normalizedOriginal.isBlank()) {
            return true;
        }
        // If candidate became dramatically shorter, treat as suspicious AI output.
        return normalizedCandidate.length() >= Math.max(1, normalizedOriginal.length() / 3);
    }

    private static String normalizeWhitespaceForCheck(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim();
    }

    private static boolean shouldUseAiPlaceholderInflection(TranslationService.TranslationBackend backend,
                                                            List<String> stage,
                                                            List<Integer> tokenIndexes) {
        if (tokenIndexes == null || tokenIndexes.isEmpty()) {
            return false;
        }
        if (backend != TranslationService.TranslationBackend.GEMINI) {
            return true;
        }
        if (tokenIndexes.size() > GEMINI_AI_INFLECTION_MAX_ITEMS) {
            AppLog.info("[Glossary] Skip AI placeholder inflection for Gemini: items="
                    + tokenIndexes.size() + " > " + GEMINI_AI_INFLECTION_MAX_ITEMS);
            return false;
        }

        int totalChars = 0;
        for (Integer idx : tokenIndexes) {
            if (idx == null || stage == null || idx < 0 || idx >= stage.size()) {
                continue;
            }
            String value = stage.get(idx);
            if (value != null) {
                totalChars += value.length();
                if (totalChars > GEMINI_AI_INFLECTION_MAX_CHARS) {
                    AppLog.info("[Glossary] Skip AI placeholder inflection for Gemini: chars="
                            + totalChars + " > " + GEMINI_AI_INFLECTION_MAX_CHARS);
                    return false;
                }
            }
        }
        return true;
    }

    private static List<String> tryResolvePlaceholdersWithAi(List<String> textsWithTokens,
                                                             List<Map<String, String>> tokenMaps,
                                                             String targetLang) {
        if (textsWithTokens == null || textsWithTokens.isEmpty() || tokenMaps == null || tokenMaps.isEmpty()) {
            return null;
        }

        boolean hasAnyTokens = false;
        for (Map<String, String> map : tokenMaps) {
            if (map != null && !map.isEmpty()) {
                hasAnyTokens = true;
                break;
            }
        }
        if (!hasAnyTokens) {
            return null;
        }

        OkHttpClient http = TranslationService.sharedHttpClient().newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .readTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(12))
                .build();

        TranslationService.TranslationBackend backend = TranslationService.getSelectedBackend();

        if (backend == TranslationService.TranslationBackend.GEMINI) {
            try {
                if (GeminiTranslationProvider.isConfigured()) {
                    return GeminiTranslationProvider.inflectGlossaryPlaceholders(textsWithTokens, tokenMaps, targetLang, http);
                }
            } catch (IOException ex) {
                AppLog.warn("[Glossary] AI inflection via Gemini failed: " + ex.getMessage());
            }
            return null;
        }

        if (backend == TranslationService.TranslationBackend.SILICONFLOW) {
            try {
                if (SiliconFlowTranslationProvider.isConfigured()) {
                    return SiliconFlowTranslationProvider.inflectGlossaryPlaceholders(textsWithTokens, tokenMaps, targetLang, http);
                }
            } catch (IOException ex) {
                AppLog.warn("[Glossary] AI inflection via SiliconFlow failed: " + ex.getMessage());
            }
            return null;
        }

        return null;
    }

    private static String replaceGlossaryToken(String text,
                                               String token,
                                               String baseTerm,
                                               boolean useRussianInflection) {
        return RussianGlossaryInflector.replaceGlossaryToken(
                text,
                token,
                baseTerm,
                useRussianInflection
        );
    }

    private static String recoverBrokenGlossaryTokens(String text, FrozenTerms frozen) {
        if (isBlank(text) || frozen == null || frozen.tokenToTarget().isEmpty()) {
            return text;
        }

        Matcher matcher = BROKEN_SC2_TERM_TOKEN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String token = "__SC2_TERM_" + matcher.group(1) + "__";
            String replacement = frozen.tokenToTarget().containsKey(token) ? token : matcher.group();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String restoreTokenSpacingFromTemplate(String text, FrozenTerms frozen) {
        if (isBlank(text) || frozen == null || frozen.tokenToTarget().isEmpty()) {
            return text;
        }

        String template = frozen.preparedText();
        if (isBlank(template)) {
            return text;
        }

        String out = text;
        int templateSearchFrom = 0;
        int outSearchFrom = 0;

        for (String token : frozen.tokenToTarget().keySet()) {
            int templatePos = template.indexOf(token, templateSearchFrom);
            if (templatePos < 0) {
                continue;
            }

            int outPos = out.indexOf(token, outSearchFrom);
            if (outPos < 0) {
                templateSearchFrom = templatePos + token.length();
                continue;
            }

            if (hasWhitespaceBefore(template, templatePos) && outPos > 0 && !Character.isWhitespace(out.charAt(outPos - 1))) {
                out = out.substring(0, outPos) + " " + out.substring(outPos);
                outPos++;
            }

            int tokenEnd = outPos + token.length();
            if (hasWhitespaceAfter(template, templatePos + token.length(), template.length())
                    && tokenEnd < out.length()
                    && !Character.isWhitespace(out.charAt(tokenEnd))) {
                out = out.substring(0, tokenEnd) + " " + out.substring(tokenEnd);
                tokenEnd++;
            }

            templateSearchFrom = templatePos + token.length();
            outSearchFrom = tokenEnd;
        }

        return out;
    }

    private static boolean hasWhitespaceBefore(String text, int index) {
        return index > 0 && Character.isWhitespace(text.charAt(index - 1));
    }

    private static boolean hasWhitespaceAfter(String text, int index, int length) {
        return index < length && Character.isWhitespace(text.charAt(index));
    }
    private static String replaceWholeWordUnicode(String input, String needle, String replacement) {
        String pattern = "(?iu)(?<![\\p{L}\\p{N}])"
                + java.util.regex.Pattern.quote(needle)
                + "(?![\\p{L}\\p{N}])";
        return input.replaceAll(pattern, java.util.regex.Matcher.quoteReplacement(replacement));
    }

    private static boolean containsWholeWordUnicode(String input, String needle) {
        if (isBlank(input) || isBlank(needle)) {
            return false;
        }
        String pattern = "(?iu)(?<![\\p{L}\\p{N}])"
                + Pattern.quote(needle)
                + "(?![\\p{L}\\p{N}])";
        return Pattern.compile(pattern).matcher(input).find();
    }

    private FreezeWordResult freezeWordsByGlossary(String sourceLang,
                                                   String targetLang,
                                                   String input,
                                                   Map<String, String> tokenToTarget,
                                                   int counterStart) {
        if (isBlank(input) || isBlank(sourceLang) || isBlank(targetLang)) {
            return new FreezeWordResult(input, counterStart);
        }

        Matcher matcher = WORD_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        int counter = counterStart;

        while (matcher.find()) {
            String word = matcher.group();
            if (isBlank(word)) continue;

            String hit = findWordMatch(sourceLang, word, targetLang);
            if (isBlank(hit)) continue;

            String token = "__SC2_TERM_" + counter + "__";
            tokenToTarget.putIfAbsent(token, hit);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
            counter++;
        }

        matcher.appendTail(sb);
        return new FreezeWordResult(sb.toString(), counter);
    }

    private static final class FreezeWordResult {
        private final String text;
        private final int nextCounter;

        private FreezeWordResult(String text, int nextCounter) {
            this.text = text;
            this.nextCounter = nextCounter;
        }

        private String text() {
            return text;
        }

        private int nextCounter() {
            return nextCounter;
        }
    }
    public String findSmartMatch(Category category,
                                 String key,
                                 String sourceLang,
                                 String sourceText,
                                 String targetLang) {
        if (category == null || isBlank(sourceLang) || isBlank(targetLang) || isBlank(sourceText)) {
            return null;
        }

        String direct = findBestMatch(category, key, sourceLang, sourceText, targetLang);
        if (!isBlank(direct)) {
            return direct;
        }

        // Try composition with MT fallback for untranslated words
        String composed = composePhraseWithMtFallback(sourceLang, sourceText, targetLang);
        if (!isBlank(composed)) {
            return composed;
        }

        String srcLangNorm = normalizeLang(sourceLang);
        String trgLangNorm = normalizeLang(targetLang);
        String srcNorm = normalizeText(sourceText);
        if (isBlank(srcNorm)) {
            return null;
        }

        Set<String> sourceTokens = tokenizeNormalized(srcNorm);
        if (sourceTokens.isEmpty()) {
            return null;
        }

        double bestScore = 0.0;
        String bestTarget = null;

        for (GlossaryRow row : rows) {
            if (row == null) continue;
            if (row.category != category) continue;

            String candidateSource = row.textsByLang.get(srcLangNorm);
            String candidateTarget = row.textsByLang.get(trgLangNorm);

            if (isBlank(candidateSource) || isBlank(candidateTarget)) continue;

            String candNorm = normalizeText(candidateSource);
            if (isBlank(candNorm)) continue;

            if (srcNorm.equals(candNorm)) {
                return candidateTarget.trim();
            }

            double score = similarityScore(
                    srcNorm,
                    candNorm,
                    sourceTokens,
                    tokenizeNormalized(candNorm),
                    key,
                    row.key
            );

            if (score > bestScore) {
                bestScore = score;
                bestTarget = candidateTarget.trim();
            }
        }

        return bestScore >= 0.82 ? bestTarget : null;
    }
    private double similarityScore(String srcNorm,
                                   String candNorm,
                                   Set<String> sourceTokens,
                                   Set<String> candidateTokens,
                                   String sourceKey,
                                   String candidateKey) {
        double score = 0.0;

        // 1. contains / substring
        if (srcNorm.contains(candNorm) || candNorm.contains(srcNorm)) {
            double shorter = Math.min(srcNorm.length(), candNorm.length());
            double longer = Math.max(srcNorm.length(), candNorm.length());
            double ratio = longer == 0 ? 0.0 : (shorter / longer);
            score += 0.35 + (0.20 * ratio);
        }

        // 2. token overlap
        double jaccard = jaccard(sourceTokens, candidateTokens);
        score += 0.45 * jaccard;

        // 3. length similarity
        double lenSim = lengthSimilarity(srcNorm, candNorm);
        score += 0.10 * lenSim;

        // 4. prefix/key bonus
        if (sameKeyFamily(sourceKey, candidateKey)) {
            score += 0.08;
        }

        // 5. starts/ends similar bonus
        if (startsOrEndsSimilarly(srcNorm, candNorm)) {
            score += 0.07;
        }

        return Math.min(score, 1.0);
    }
    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        int intersection = 0;
        for (String s : a) {
            if (b.contains(s)) {
                intersection++;
            }
        }

        int union = a.size() + b.size() - intersection;
        if (union <= 0) return 0.0;

        return (double) intersection / union;
    }
    private static double lengthSimilarity(String a, String b) {
        int la = a == null ? 0 : a.length();
        int lb = b == null ? 0 : b.length();
        int max = Math.max(la, lb);
        if (max == 0) return 1.0;
        return 1.0 - ((double) Math.abs(la - lb) / max);
    }
    private static boolean sameKeyFamily(String k1, String k2) {
        if (isBlank(k1) || isBlank(k2)) return false;

        String a = normalizeKey(k1);
        String b = normalizeKey(k2);

        String pa = keyFamilyPrefix(a);
        String pb = keyFamilyPrefix(b);

        return !isBlank(pa) && pa.equals(pb);
    }
    private static String keyFamilyPrefix(String key) {
        if (isBlank(key)) return "";

        int idx = key.lastIndexOf('/');
        if (idx <= 0) return key;

        return key.substring(0, idx);
    }
    private static boolean startsOrEndsSimilarly(String a, String b) {
        if (isBlank(a) || isBlank(b)) return false;

        return a.startsWith(b)
                || b.startsWith(a)
                || a.endsWith(b)
                || b.endsWith(a);
    }
    private static Set<String> tokenizeNormalized(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (isBlank(text)) return out;

        String[] parts = text.split("[\\s\\-_/.,:;!?()\\[\\]{}\"'Ć„ā€ Äā‚¬Ė›Ć„ĀÄā‚¬ĀÄ€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā«Ć„ā€ Äā‚¬Ė›Ć„ĀÄā‚¬ĀÄ€Ā¬Ä†ā€˛Äā€Ā¬Ć„ā‚¬Ä€Ā»]+");
        for (String p : parts) {
            String t = p == null ? "" : p.trim();
            if (t.length() >= 2) {
                out.add(t);
            }
        }
        return out;
    }
    @SuppressWarnings("unused")
    private List<Category> categoriesForComposition(Category category) {
        if (category == null) {
            return List.of(Category.UNIT, Category.BUTTON, Category.ABILITY);
        }

        return switch (category) {
            case ABILITY -> List.of(Category.ABILITY, Category.UNIT, Category.BUTTON);
            case BUTTON  -> List.of(Category.BUTTON, Category.UNIT, Category.ABILITY);
            case UNIT    -> List.of(Category.UNIT, Category.ABILITY, Category.BUTTON);
        };
    }
}
