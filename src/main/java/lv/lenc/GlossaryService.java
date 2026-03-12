package lv.lenc;

import javafx.beans.property.*;
import javafx.concurrent.Task;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.text.Normalizer;
import java.util.*;


import java.util.List;
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


    public enum Category {
        UNIT, BUTTON, ABILITY
    }

    private static final Set<String> LANGS = new LinkedHashSet<>(Arrays.asList(
            "enus", "ruru", "dede", "eses", "esmx", "frfr",
            "itit", "kokr", "plpl", "ptbr", "zhcn", "zhtw"
    ));

    private final Map<LookupKey, String> exactMap = new HashMap<>();
    private final Map<TextOnlyLookupKey, String> textOnlyMap = new HashMap<>();
    private final BooleanProperty glossaryLoading = new SimpleBooleanProperty(false);
    private final BooleanProperty glossaryReady = new SimpleBooleanProperty(false);
    private final StringProperty glossaryStatus = new SimpleStringProperty("Glossary not load");
    private final List<GlossaryRow> rows = new ArrayList<>();
    private final Map<TermLookupKey, List<TermEntry>> termIndex = new HashMap<>();
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
                System.out.println("[Glossary] Resource not found: " + resourcePath);
                return;
            }
            loadFromStream(is, category, resourcePath);
        } catch (Exception e) {
            System.out.println("[Glossary] Failed to load " + resourcePath + ": " + e.getMessage());
        }
    }

    public void loadFromStream(InputStream is, Category category, String sourceName) throws IOException {
        List<String[]> csvRows = readSemicolonCsv(is);

        if (csvRows.isEmpty()) {
            System.out.println("[Glossary] Empty CSV: " + sourceName);
            return;
        }

        int headerIndex = findRealHeaderIndex(csvRows);
        if (headerIndex < 0) {
            System.out.println("[Glossary] Header not found in: " + sourceName);
            return;
        }

        String[] header = csvRows.get(headerIndex);
        Map<String, Integer> col = indexColumns(header);

        if (!col.containsKey("key")) {
            System.out.println("[Glossary] No 'key' column in: " + sourceName);
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

        System.out.println("[Glossary] Loaded " + imported + " mappings from " + sourceName
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
    public String findBestMatch(Category category,
                                String key,
                                String sourceLang,
                                String sourceText,
                                String targetLang) {
        if (category == null || isBlank(sourceLang) || isBlank(targetLang)) {
            return null;
        }

        String cleanedSource = cleanGlossaryText(sourceText);
        if (isBlank(cleanedSource)) return null;

        // 1. Сначала точный поиск по key
        if (!isBlank(key)) {
            String exact = findExact(category, key, sourceLang, sourceText, targetLang);
            if (!isBlank(exact)) {
                return exact;
            }
        }

        // 2. Если не нашли — ищем только по тексту
        TextOnlyLookupKey tlk = new TextOnlyLookupKey(
                category,
                normalizeLang(sourceLang),
                normalizeText(cleanedSource),
                normalizeLang(targetLang)
        );

        String hit = textOnlyMap.get(tlk);
        return isBlank(hit) ? null : hit;
    }
    public String findBestMatch(String key,
                                String sourceLang,
                                String sourceText,
                                String targetLang) {
        Category category = detectCategory(key);
        if (category != null) {
            return findBestMatch(category, key, sourceLang, sourceText, targetLang);
        }

        String cleanedSource = cleanGlossaryText(sourceText);
        if (isBlank(cleanedSource) || isBlank(sourceLang) || isBlank(targetLang)) {
            return null;
        }

        for (Category cat : Category.values()) {
            String exact = findExact(cat, key, sourceLang, sourceText, targetLang);
            if (!isBlank(exact)) {
                return exact;
            }

            TextOnlyLookupKey tlk = new TextOnlyLookupKey(
                    cat,
                    normalizeLang(sourceLang),
                    normalizeText(cleanedSource),
                    normalizeLang(targetLang)
            );

            String hit = textOnlyMap.get(tlk);
            if (!isBlank(hit)) {
                return hit;
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
     * "Заку'Дас - лёгкий мост /// Zhakul'Das Light Bridge" -> "Заку'Дас - лёгкий мост"
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
        return stripBom(key).trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeLang(String lang) {
        return stripBom(lang).trim().toLowerCase(Locale.ROOT);
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
        glossaryStatus.set("Загрузка глоссариев...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                clear();

                for (Path file : files) {
                    Category category = detectCategoryByFileName(file.getFileName().toString());
                    if (category == null) {
                        continue;
                    }

                    try (InputStream in = Files.newInputStream(file)) {
                        loadFromStream(in, category, file.getFileName().toString());
                    }
                }

                //    buildAutoTerms();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            glossaryLoading.set(false);
            glossaryReady.set(true);
            glossaryStatus.set("Глоссарии загружены");
            System.out.println("[Glossary] success, loading=" + glossaryLoading.get() + ", ready=" + glossaryReady.get());
        });

        task.setOnFailed(e -> {
            glossaryLoading.set(false);
            glossaryReady.set(false);
            glossaryStatus.set("Ошибка загрузки глоссариев");
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(task, "glossary-loader");
        thread.setDaemon(true);
        thread.start();
    }
    public void clear() {
        exactMap.clear();
        textOnlyMap.clear();
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
        glossaryStatus.set("Загрузка глоссариев...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                clear();
                loadFromResource("/glossary/Ability_Localization_KSP.csv", Category.ABILITY);
                loadFromResource("/glossary/Button_Localization_KSP.csv", Category.BUTTON);
                loadFromResource("/glossary/Units_Localization_KSP.csv", Category.UNIT);
                //    buildAutoTerms();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            glossaryLoading.set(false);
            glossaryReady.set(true);
            glossaryStatus.set("Глоссарии загружены");
        });

        task.setOnFailed(e -> {
            glossaryLoading.set(false);
            glossaryReady.set(false);
            glossaryStatus.set("Ошибка загрузки глоссариев");
            if (task.getException() != null) {
                task.getException().printStackTrace();
            }
        });

        Thread thread = new Thread(task, "glossary-loader");
        thread.setDaemon(true);
        thread.start();
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
        final String normalizedSource;
        final int priority;

        TermEntry(String sourceText, String targetText, String normalizedSource, int priority) {
            this.sourceText = sourceText;
            this.targetText = targetText;
            this.normalizedSource = normalizedSource;
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
                                    normalizeText(c.sourceText),
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

        System.out.println("[Glossary] Auto terms built: " + termIndex.size() + " buckets");
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
        if (category == null || isBlank(sourceLang) || isBlank(targetLang) || isBlank(text)) {
            return new FrozenTerms(text, Collections.emptyMap());
        }

        TermLookupKey key = new TermLookupKey(
                category,
                normalizeLang(sourceLang),
                normalizeLang(targetLang)
        );

        List<TermEntry> terms = termIndex.getOrDefault(key, Collections.emptyList());
        if (terms.isEmpty()) {
            return new FrozenTerms(text, Collections.emptyMap());
        }

        String out = text;
        Map<String, String> tokenToTarget = new LinkedHashMap<>();
        int counter = 0;

        for (TermEntry term : terms) {
            String token = "__SC2_TERM_" + counter + "__";
            String replaced = replaceWholeWordUnicode(out, term.sourceText, token);

            if (!replaced.equals(out)) {
                out = replaced;
                tokenToTarget.put(token, term.targetText);
                counter++;
            }
        }

        return new FrozenTerms(out, tokenToTarget);
    }
    public String unfreezeTerms(String text, FrozenTerms frozen) {
        if (isBlank(text) || frozen == null || frozen.tokenToTarget().isEmpty()) {
            return text;
        }

        String out = text;
        for (Map.Entry<String, String> e : frozen.tokenToTarget().entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }
    private static String replaceWholeWordUnicode(String input, String needle, String replacement) {
        String pattern = "(?iu)(?<![\\p{L}\\p{N}])"
                + java.util.regex.Pattern.quote(needle)
                + "(?![\\p{L}\\p{N}])";
        return input.replaceAll(pattern, java.util.regex.Matcher.quoteReplacement(replacement));
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

        String[] parts = text.split("[\\s\\-_/.,:;!?()\\[\\]{}\"'«»]+");
        for (String p : parts) {
            String t = p == null ? "" : p.trim();
            if (t.length() >= 2) {
                out.add(t);
            }
        }
        return out;
    }
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
