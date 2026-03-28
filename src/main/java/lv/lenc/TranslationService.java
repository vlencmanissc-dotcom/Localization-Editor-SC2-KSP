// TranslationService.java
package lv.lenc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.jsoup.parser.Parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class TranslationService {


    public static final String DEFAULT_BASE_URL = "http://127.0.0.1:5000/";
    public static final String GPU_FALLBACK_BASE_URL = "http://127.0.0.1:5001/";
    public static volatile String BASE_URL = DEFAULT_BASE_URL;
    public static final String SEP = "\n";
    private static final double PERFORMANCE_LOAD_FACTOR = 1.0;
    private static final int BATCH_MAX_ITEMS = 220;
    private static final int BATCH_MAX_CHARS = 18_000;
    private static final int GPU_BATCH_MAX_ITEMS = 220;
    private static final int GPU_BATCH_MAX_CHARS = 18_000;
    private static final int TRANSLATION_CACHE_LIMIT = 20_000;
    // We no longer send tags. Additionally protect "geometric" symbols (progress bars).
    private static final Pattern GEOM_RUN = Pattern.compile("[\\u2580-\\u259F\\u25A0-\\u25FF]+");
    private static final AtomicReference<Call<?>> inFlight = new AtomicReference<>();
    private static final AtomicInteger PERFORMANCE_MODE_LEASES = new AtomicInteger();
    private static final OkHttpClient HTTP = buildTranslationClient();
    private static final OkHttpClient HEALTH_HTTP = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(4))
            .writeTimeout(Duration.ofSeconds(4))
            .callTimeout(Duration.ofSeconds(5))
            .retryOnConnectionFailure(true)
            .build();

    public static volatile LibreTranslateApi api;
    private static volatile Process managedLtProcess;
    private static volatile boolean managedLtGpu;
    private static volatile boolean activeEndpointGpu;
    private static volatile String cachedPythonExecutable;
    private static final long CAPABILITY_CACHE_TTL_MS = 180_000L;
    private static volatile long capabilityCacheTs;
    private static volatile boolean cachedNvidiaGpu;
    private static volatile boolean cachedDockerUsable;
    private static volatile boolean cachedLocalPythonCuda;
    private static volatile boolean shutdownRequested;
    private static final Object CACHE_IO_LOCK = new Object();
    private static volatile boolean persistentCacheLoaded;
    private static volatile boolean shutdownHookInstalled;
    private static volatile boolean persistentCacheEnabled = false; // default: do not persist on disk (as requested)
    private static volatile boolean useGpuDocker = false; // default: avoid docker-induced deadlock by default
    private static volatile boolean gpuDockerStartupFailed;
    private static final Map<String, String> TRANSLATION_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > TRANSLATION_CACHE_LIMIT;
                }
            });

    static {
        api = createApi(BASE_URL);
        refreshActiveEndpoint();

        // respect settings persistence flag from saved settings
        try {
            persistentCacheEnabled = SettingsManager.loadTranslationCachePersistence();
        } catch (Exception ignored) {
            persistentCacheEnabled = true;
        }

        // respect GPU Docker mode flag from settings
        try {
            useGpuDocker = SettingsManager.loadUseGpuDocker();
        } catch (Exception ignored) {
            useGpuDocker = false;
        }

        ensurePersistentCacheLoaded();
        installCacheShutdownHook();
    }

    private static OkHttpClient buildTranslationClient() {
        int cores = Runtime.getRuntime().availableProcessors();
        int perHost = Math.max(4, Math.min(64, (int) Math.ceil(cores * PERFORMANCE_LOAD_FACTOR * 2.0)));
        int total = Math.max(perHost + 4, Math.min(96, perHost * 2));

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(total);
        dispatcher.setMaxRequestsPerHost(perHost);

        return new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(120))
                .callTimeout(Duration.ofSeconds(150))
                .retryOnConnectionFailure(true)
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("Accept", "application/json")
                                .build()))
                .build();
    }

    private static LibreTranslateApi createApi(String baseUrl) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .addConverterFactory(GsonConverterFactory.create())
                .client(HTTP)
                .build();
        return retrofit.create(LibreTranslateApi.class);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }

        String normalized = baseUrl.trim();
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private static boolean isGpuBaseUrl(String baseUrl) {
        return normalizeBaseUrl(baseUrl).equals(normalizeBaseUrl(GPU_FALLBACK_BASE_URL));
    }

    public static boolean isGpuActive() {
        return activeEndpointGpu;
    }

    public static synchronized void configureStartupLanguages(java.util.Collection<String> languageCodes) {
        // Language preloading is intentionally disabled.
        // The app only starts the server early; models are loaded on first real request.
    }

    private static boolean inferGpuForEndpoint(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (isGpuBaseUrl(normalized)) {
            return true;
        }
        if (!normalized.equals(normalizeBaseUrl(DEFAULT_BASE_URL))) {
            return false;
        }
        Process process = managedLtProcess;
        return managedLtGpu && process != null && process.isAlive();
    }

    private static int effectiveBatchMaxItems() {
        return isGpuActive() ? GPU_BATCH_MAX_ITEMS : BATCH_MAX_ITEMS;
    }

    private static int effectiveBatchMaxChars() {
        return isGpuActive() ? GPU_BATCH_MAX_CHARS : BATCH_MAX_CHARS;
    }

    private static int effectiveConcurrency() {
        int cores = Runtime.getRuntime().availableProcessors();
        int target = (int) Math.ceil(cores * PERFORMANCE_LOAD_FACTOR * 2.0);
        return Math.max(2, Math.min(64, target));
    }

    private static List<String> translatePreservingTagsBatchedSequential(
            LibreTranslateApi api,
            List<String> texts,
            String source,
            String target,
            java.util.function.BooleanSupplier stop,
            ProgressListener progress
    ) throws IOException, InterruptedException {
        // Sequential mode for small jobs and fallback.
        DedupedTexts deduped = dedupeTexts(texts);
        List<String> uniqueTexts = deduped.uniqueTexts;

        int maxItems = effectiveBatchMaxItems();
        int maxChars = effectiveBatchMaxChars();
        List<List<String>> parts = chunksByChars(uniqueTexts, maxItems, maxChars);

        List<String> uniqueOut = new ArrayList<>(uniqueTexts.size());
        int batchNo = 0;
        int total = parts.size();

        for (List<String> part : parts) {
            batchNo++;
            long t0 = System.currentTimeMillis();

            IOException last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                if (stop != null && stop.getAsBoolean()) return expandDeduped(uniqueOut, deduped, texts.size());

                try {
                    List<String> translated = translatePreservingTags(api, part, source, target);
                    uniqueOut.addAll(translated);
                    long took = System.currentTimeMillis() - t0;
                    AppLog.info("[LT] preserveTags.batch " + batchNo + " OK in " + took + "ms");
                    break;
                } catch (IOException e) {
                    last = e;
                    long sleep = Math.min(700L * (1L << (attempt - 1)), 5_000L);
                    refreshActiveEndpoint();
                    Thread.sleep(sleep);
                    if (stop != null && stop.getAsBoolean()) return expandDeduped(uniqueOut, deduped, texts.size());
                    if (attempt == 3) throw last;
                }
            }

            if (progress != null) {
                progress.onProgress(batchNo / (double) total, "batch " + batchNo + "/" + total);
            }
        }

        if (progress != null) {
            progress.onProgress(1.0, "done");
        }

        return expandDeduped(uniqueOut, deduped, texts.size());
    }

    private static List<String> endpointCandidates() {
        LinkedHashSet<String> urls = new LinkedHashSet<>();

        String sys = System.getProperty("lt.baseUrl");
        if (sys != null && !sys.isBlank()) {
            urls.add(normalizeBaseUrl(sys));
        }

        String env = System.getenv("LT_BASE_URL");
        if (env != null && !env.isBlank()) {
            urls.add(normalizeBaseUrl(env));
        }

        urls.add(GPU_FALLBACK_BASE_URL);
        urls.add(normalizeBaseUrl(BASE_URL));
        urls.add(DEFAULT_BASE_URL);
        return new ArrayList<>(urls);
    }

    private static boolean probeBaseUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        Request request = new Request.Builder()
                .url(normalized + "languages")
                .get()
                .build();

        try (okhttp3.Response response = HEALTH_HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return false;
            }

            return response.body() != null && !response.body().string().isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void activateEndpoint(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        boolean gpu = inferGpuForEndpoint(normalized);
        activeEndpointGpu = gpu;
        if (!normalized.equals(BASE_URL)) {
            BASE_URL = normalized;
            api = createApi(BASE_URL);
            AppLog.info("[LT] active endpoint switched to " + BASE_URL
                    + " (" + (gpu ? "GPU" : "CPU") + ")");
        }
    }

    private static boolean waitForSpecificEndpoint(String baseUrl, int attempts, long sleepMs) throws InterruptedException {
        String normalized = normalizeBaseUrl(baseUrl);
        for (int i = 0; i < attempts; i++) {
            if (probeBaseUrl(normalized)) {
                activateEndpoint(normalized);
                return true;
            }
            Thread.sleep(sleepMs);
        }
        return false;
    }

    public static synchronized boolean refreshActiveEndpoint() {
        for (String candidate : endpointCandidates()) {
            if (!probeBaseUrl(candidate)) {
                continue;
            }

            activateEndpoint(candidate);
            return true;
        }
        return false;
    }

    public static LibreTranslateApi requireApi() throws IOException {
        // Hot path: translation batches may call this many times in parallel.
        // Avoid a blocking /languages probe on every request and only refresh the
        // endpoint when we have no API instance yet. Real request failures already
        // trigger refreshActiveEndpoint() and retries in the batch executors.
        LibreTranslateApi current = api;
        if (current != null) {
            return current;
        }

        synchronized (TranslationService.class) {
            current = api;
            if (current != null) {
                return current;
            }
            if (refreshActiveEndpoint()) {
                return api;
            }
        }

        throw new IOException("LibreTranslate is not reachable on " + endpointCandidates());
    }
    private static void splitTextByGeomAndQueue(
            String text,
            List<String> tokens,
            List<Integer> textPositions,
            List<TagFreezer.Frozen> frozenParts,
            List<String> toTranslate
    ) {
        if (text == null || text.isEmpty()) {
            tokens.add("");
            return;
        }

        java.util.regex.Matcher gm = GEOM_RUN.matcher(text);
        int tp = 0;

        while (gm.find()) {
            if (gm.start() > tp) {
                String t1 = text.substring(tp, gm.start());

                TagFreezer.Frozen f1 = TagFreezer.freezeRich(t1);
                String clean1 = sanitizeVisible(f1.protectedText);

                tokens.add(t1);
                if (!clean1.isEmpty()) {
                    textPositions.add(tokens.size() - 1);
                    frozenParts.add(f1);
                    toTranslate.add(clean1);
                }
            }

            // Geometric Unicode run stays untouched.
            tokens.add(gm.group());
            tp = gm.end();
        }

        if (tp < text.length()) {
            String t2 = text.substring(tp);

            TagFreezer.Frozen f2 = TagFreezer.freezeRich(t2);
            String clean2 = sanitizeVisible(f2.protectedText);

            tokens.add(t2);
            if (!clean2.isEmpty()) {
                textPositions.add(tokens.size() - 1);
                frozenParts.add(f2);
                toTranslate.add(clean2);
            }
        }
    }
    public static void cancelInFlight() {
        Call<?> c = inFlight.getAndSet(null);
        if (c != null) c.cancel(); // interrupts current execute() -> throws exception
    }

    // example usage inside translatePreservingTags/translate...
    private static <T> T executeTracked(Call<T> call) throws IOException {
        inFlight.set(call);
        try {
            return call.execute().body();
        } finally {
            inFlight.compareAndSet(call, null);
        }
    }
    private static final Pattern TAG_LIKE = Pattern.compile("(?s)<\\s*[/]?\\w+[^>]*>");

    private static boolean looksLikeHtml(String s) {
        // filter out cases like "2 < 5"
        if (s == null || s.indexOf('<') < 0 || s.indexOf('>') < 0) return false;
        return TAG_LIKE.matcher(s).find();
    }

    // Rebuild plan: either plain string or HTML fragment with text nodes
    private static abstract class RebuildPlan {
        abstract void consumeTranslated(ListIterator<String> it);
        abstract String result();
    }
    private static class PlainPlan extends RebuildPlan {
        private final TagFreezer.Frozen frozen;
        private String translated;

        PlainPlan(TagFreezer.Frozen frozen) {
            this.frozen = frozen;
        }

        @Override
        public void consumeTranslated(ListIterator<String> it) {
            String t = it.next();
            if (t == null) t = "";

            t = t.replace('\u00A0', ' ')
                    .replaceAll("[\\r\\n]+", " ")
                    .trim();

            translated = TagFreezer.unfreezeRich(t, frozen);
        }

        @Override
        public String result() {
            return translated;
        }
    }


    private static String sanitizeVisible(String s) {
        if (s == null) return "";

        // 0) decode HTML entities
        s = Parser.unescapeEntities(s, true);

        // 1) normalize Unicode form
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);

        // 2) remove invisible/control characters only
        //    (DO NOT touch Block Elements U+2580Äā‚¬ā€259F and Geometric Shapes U+25A0Äā‚¬ā€25FF!)
        s = s.replace('\u00A0', ' ')
                .replaceAll("[\\u0000-\\u001F\\u007F\\u200B-\\u200F\\u2028\\u2029\\u2060\\uFEFF]", "");

        // 3) collapse line breaks and multiple spaces
        s = s.replaceAll("[\\r\\n]+", " ").replaceAll("\\s+", " ").trim();

        // 4) normalize spacing around punctuation
        s = normalizePunctuationSpacing(s);

        return s;
    }

    private static String normalizePunctuationSpacing(String s) {
        // remove spaces BEFORE punctuation
        s = s.replaceAll("\\s+([,:;!?])", "$1");
        // ensure single space AFTER (if followed by letter/digit/quote)
        s = s.replaceAll("([,:;!?])(?!\\s|$)", "$1 ");
        // remove spaces after opening brackets/quotes and before closing ones
        s = s.replaceAll("([\\(\\[\\{Ä€Ā«])\\s+", "$1")
                .replaceAll("\\s+([\\)\\]\\}Ä€Ā»])", "$1");
        return s;
    }
    // Pass through sanitizeVisible: normalization, invisible chars, spacing.
    private static class PrepResult {
        final List<String> toTranslate;
        final List<RebuildPlan> plans;
        PrepResult(List<String> toTranslate, List<RebuildPlan> plans) {
            this.toTranslate = toTranslate; this.plans = plans;
        }
    }

    private static class DedupedTexts {
        final List<String> uniqueTexts;
        final int[] mapToUnique;

        DedupedTexts(List<String> uniqueTexts, int[] mapToUnique) {
            this.uniqueTexts = uniqueTexts;
            this.mapToUnique = mapToUnique;
        }
    }
    // Token: <...> (any tag, including self-closing). No parsing, just extract.
    private static final Pattern TAG_TOKEN = Pattern.compile("(?s)<[^>]*?>");

    // Rebuild plan: tags + text, only text is translated
// Rebuild plan for HTML-like strings:
// tags stay untouched, only text fragments are translated and then restored.
    private static class TokenPlan extends RebuildPlan {
        private final List<String> tokens;
        private final List<Integer> textPositions;
        private final List<TagFreezer.Frozen> frozenParts;

        TokenPlan(List<String> tokens,
                  List<Integer> textPositions,
                  List<TagFreezer.Frozen> frozenParts) {
            this.tokens = tokens;
            this.textPositions = textPositions;
            this.frozenParts = frozenParts;
        }

        @Override
        public void consumeTranslated(ListIterator<String> it) {
            for (int i = 0; i < textPositions.size(); i++) {
                int pos = textPositions.get(i);

                String t = it.next();
                if (t == null) t = "";

                t = t.replace('\u00A0', ' ')
                        .replaceAll("[\\r\\n]+", " ")
                        .trim();

                t = TagFreezer.unfreezeRich(t, frozenParts.get(i));
                tokens.set(pos, t);
            }
        }

        @Override
        public String result() {
            StringBuilder sb = new StringBuilder();
            for (String tk : tokens) sb.append(tk);
            return sb.toString();
        }
    }

    private static PrepResult prepareForTranslation(List<String> inputs) {
        List<String> toTranslate = new ArrayList<>();
        List<RebuildPlan> plans = new ArrayList<>(inputs.size());

        int htmlCnt = 0, plainCnt = 0;

        for (int i = 0; i < inputs.size(); i++) {
            String s = inputs.get(i);

            if (looksLikeHtml(s)) {
                htmlCnt++;

                List<String> tokens = new ArrayList<>();
                List<Integer> textPositions = new ArrayList<>();
                List<TagFreezer.Frozen> frozenParts = new ArrayList<>();

                int pos = 0;
                java.util.regex.Matcher m = TAG_TOKEN.matcher(s);

                while (m.find()) {
                    if (m.start() > pos) {
                        String text = s.substring(pos, m.start());
                        splitTextByGeomAndQueue(text, tokens, textPositions, frozenParts, toTranslate);
                    }

                    // Tag token itself is preserved and never translated.
                    tokens.add(s.substring(m.start(), m.end()));
                    pos = m.end();
                }

                if (pos < s.length()) {
                    String text = s.substring(pos);
                    splitTextByGeomAndQueue(text, tokens, textPositions, frozenParts, toTranslate);
                }

                plans.add(new TokenPlan(tokens, textPositions, frozenParts));

            } else {
                plainCnt++;

                TagFreezer.Frozen frozen = TagFreezer.freezeRich(s == null ? "" : s);
                plans.add(new PlainPlan(frozen));
                toTranslate.add(sanitizeVisible(frozen.protectedText));
            }
        }

        AppLog.info("[LT] prepare: inputs=" + inputs.size() +
                " htmlItems=" + htmlCnt + " plainItems=" + plainCnt +
                " toTranslate=" + toTranslate.size());

        return new PrepResult(toTranslate, plans);
    }
    public static Map<String, List<String>> translateAll(
            List<String> texts,
            String sourceIso,
            List<String> targetsIso,
            java.util.function.BooleanSupplier stop,
            ProgressListener progress
    ) throws IOException {

        if (texts == null) texts = java.util.Collections.emptyList();
        if (targetsIso == null || targetsIso.isEmpty())
            throw new IllegalArgumentException("targetsIso is empty");

        //
        Map<String, Integer> firstIndex = new java.util.LinkedHashMap<>();
        List<String> unique = new java.util.ArrayList<>();
        int[] mapToUnique = new int[texts.size()];

        for (int i = 0; i < texts.size(); i++) {
            String t = texts.get(i);
            Integer idx = firstIndex.get(t);
            if (idx == null) {
                idx = unique.size();
                firstIndex.put(t, idx);
                unique.add(t);
            }
            mapToUnique[i] = idx;
        }

        Map<String, List<String>> out = new java.util.LinkedHashMap<>();

        int totalLangs = targetsIso.size();
        for (int i = 0; i < totalLangs; i++) {
            if (stop != null && stop.getAsBoolean()) return out;

            String targetIso = targetsIso.get(i);
            int langNo = i + 1;

            //
            List<String> uniqOut;
            try {
                uniqOut = translatePreservingTagsBatched(
                        api, unique, sourceIso, targetIso, stop,
                        (partFraction, msg) -> {
                            if (progress == null) return;

                            double all = ((langNo - 1) + partFraction) / (double) totalLangs;

                            String line1 = sourceIso + " -> " + targetIso + " (" + langNo + "/" + totalLangs + ")";
                            String line2 = (msg == null ? "" : msg);

                            progress.onProgress(all, line1 + "||" + line2);
                        }
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Translation interrupted", e);
            }

            if (stop != null && stop.getAsBoolean()) return out;

            // deduplicate restore
            List<String> fullOut = new java.util.ArrayList<>(texts.size());
            for (int k = 0; k < texts.size(); k++) {
                fullOut.add(uniqOut.get(mapToUnique[k]));
            }

            out.put(targetIso, fullOut);
        }

        return out;
    }

    static List<String> extractTranslations(JsonElement body, int expected) throws IOException {
        if (body == null || body.isJsonNull()) throw new IOException("Empty response from server");

        List<String> out = new ArrayList<>();

        if (body.isJsonArray()) {                 // [ ... ]
            for (JsonElement el : body.getAsJsonArray()) out.add(parseOneTranslation(el));
            return out;
        }

        if (body.isJsonObject()) {                // { ... }
            JsonObject obj = body.getAsJsonObject();
            if (obj.has("translatedText")) {
                JsonElement t = obj.get("translatedText");
                if (t.isJsonArray()) {            // {"translatedText":[...]}
                    for (JsonElement el : t.getAsJsonArray()) out.add(el.getAsString());
                    return out;
                }
                if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isString()) { // {"translatedText":"..."}
                    out.add(t.getAsString());
                    return out;
                }
            }
            if (obj.has("translations") && obj.get("translations").isJsonArray()) { // {"translations":[...]}
                for (JsonElement el : obj.get("translations").getAsJsonArray()) out.add(parseOneTranslation(el));
                return out;
            }
        }

        throw new IOException("Unexpected response format: " + body);
    }

    static String parseOneTranslation(JsonElement el) throws IOException {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return el.getAsString();
        }
        if (el.isJsonObject() && el.getAsJsonObject().has("translatedText")) {
            JsonElement t = el.getAsJsonObject().get("translatedText");
            if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isString()) return t.getAsString();
        }
        throw new IOException("Unexpected translation element: " + el);
    }
    private static List<String> rebuildFromTranslations(List<String> translated, List<RebuildPlan> plans) {
        List<String> out = new ArrayList<>(plans.size());
        ListIterator<String> it = translated.listIterator();
        for (RebuildPlan plan : plans) {
            plan.consumeTranslated(it);
            out.add(plan.result());
        }
        return out;
    }

    /**
     * Main method: translate list of strings while preserving HTML tags,
     * translating only text nodes inside.
     */
    public static List<String> translatePreservingTags(
            LibreTranslateApi api,
            List<String> inputs,
            String source,
            String target
    ) throws IOException {

        long t0 = System.currentTimeMillis();
        AppLog.info("[LT] translatePreservingTags start: items=" + inputs.size() +
                " chars=" + sumChars(inputs) + " " + source + "->" + target);

        // 
        PrepResult prep = prepareForTranslation(inputs);
        AppLog.info("[LT] prepared: toTranslate=" + prep.toTranslate.size() +
                " plans=" + prep.plans.size());

        if (prep.toTranslate.isEmpty()) {
            AppLog.info("[LT] nothing to translate, return input as-is");
            return new ArrayList<>(inputs);
        }

        List<String> translatedPrepared = new ArrayList<>(Collections.nCopies(prep.toTranslate.size(), null));
        List<String> uncachedInputs = new ArrayList<>();
        List<Integer> uncachedIndexes = new ArrayList<>();

        for (int i = 0; i < prep.toTranslate.size(); i++) {
            String preparedText = prep.toTranslate.get(i);
            String cached = cacheLookup(source, target, preparedText);
            if (cached != null) {
                translatedPrepared.set(i, cached);
            } else {
                uncachedIndexes.add(i);
                uncachedInputs.add(preparedText);
            }
        }

        if (!uncachedInputs.isEmpty()) {
            Map<String, Object> body = new HashMap<>();
            body.put("q", uncachedInputs);
            body.put("source", source);
            body.put("target", target);
            body.put("format", "text");

            LibreTranslateApi effectiveApi = requireApi();
            var resp = executeTrackedResponse(effectiveApi.translateAny(body));
            int code = (resp != null ? resp.code() : -1);
            if (resp == null || !resp.isSuccessful() || resp.body() == null) {
                String err = safeErrorBody(resp);
                throw new IOException("[LT] HTTP " + code + ": " + err);
            }

            List<String> uncachedTranslated = extractTranslations(resp.body(), uncachedInputs.size());
            AppLog.info("[LT] got " + uncachedTranslated.size() + " fresh translations for " + uncachedInputs.size()
                    + " uncached items (" + (prep.toTranslate.size() - uncachedInputs.size()) + " cache hits)");

            if (uncachedTranslated.size() != uncachedInputs.size()) {
                AppLog.info("[LT] raw: " + resp.body());
                throw new IllegalStateException("[LT] MISMATCH: in=" + uncachedInputs.size() + " out=" + uncachedTranslated.size());
            }

            for (int i = 0; i < uncachedIndexes.size(); i++) {
                int originalIndex = uncachedIndexes.get(i);
                String preparedText = prep.toTranslate.get(originalIndex);
                String translatedText = uncachedTranslated.get(i);
                translatedPrepared.set(originalIndex, translatedText);
                cacheStore(source, target, preparedText, translatedText);
            }
            AppLog.info("[LT] translation cache size now: " + TRANSLATION_CACHE.size());
        } else {
            AppLog.info("[LT] cache hit for all " + prep.toTranslate.size() + " prepared items");
        }

        List<String> out = rebuildFromTranslations(translatedPrepared, prep.plans);
        long took = System.currentTimeMillis() - t0;
        AppLog.info("[LT] translatePreservingTags done in " + took + "ms");
        return out;
    }

    private static String cacheKey(String source, String target, String text) {
        return normalizeCachePart(source) + '\u0001' + normalizeCachePart(target) + '\u0001' + (text == null ? "" : text);
    }

    private static String normalizeCachePart(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String cacheLookup(String source, String target, String text) {
        ensurePersistentCacheLoaded();
        return TRANSLATION_CACHE.get(cacheKey(source, target, text));
    }

    private static void cacheStore(String source, String target, String text, String translated) {
        ensurePersistentCacheLoaded();
        TRANSLATION_CACHE.put(cacheKey(source, target, text), translated);
    }

    private static <T> retrofit2.Response<T> executeTrackedResponse(Call<T> call) throws IOException {
        inFlight.set(call);
        try {
            return call.execute();
        } finally {
            inFlight.compareAndSet(call, null);
        }
    }

    public static List<String> translatePreservingTagsBatched(
            LibreTranslateApi api,
            List<String> texts,
            String source,
            java.util.function.BooleanSupplier stop,
            String target
    ) throws IOException, InterruptedException {

        if (texts == null || texts.isEmpty()) return Collections.emptyList();

        DedupedTexts deduped = dedupeTexts(texts);
        List<String> uniqueTexts = deduped.uniqueTexts;

        long tAll0 = System.currentTimeMillis();
        AppLog.info("[LT] preserveTags.BATCH start: items=" + texts.size() +
                " unique=" + uniqueTexts.size() +
                " chars=" + sumChars(uniqueTexts) + " " + source + "->" + target);

        int maxItems = effectiveBatchMaxItems();
        int maxChars = effectiveBatchMaxChars();
        List<List<String>> parts = chunksByChars(uniqueTexts, maxItems, maxChars);
        AppLog.info("[LT] preserveTags.batching: parts=" + parts.size()
                + " profile=" + (isGpuActive() ? "GPU" : "CPU")
                + " maxItems=" + maxItems + " maxChars=" + maxChars);

        List<String> uniqueOut = new ArrayList<>(uniqueTexts.size());
        int batchNo = 0;

        for (List<String> part : parts) {
            batchNo++;
            long t0 = System.currentTimeMillis();

            IOException last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                if (stop != null && stop.getAsBoolean()) return expandDeduped(uniqueOut, deduped, texts.size());

                try {
                    AppLog.info("[LT] preserveTags.batch " + batchNo + "/" + parts.size() +
                            " attempt " + attempt + "/3 items=" + part.size() +
                            " chars=" + sumChars(part));

                    List<String> translated = translatePreservingTags(api, part, source, target);
                    uniqueOut.addAll(translated);

                    long took = System.currentTimeMillis() - t0;
                    AppLog.info("[LT] preserveTags.batch " + batchNo + " OK in " + took + "ms");
                    break;
                } catch (IOException e) {
                    last = e;
                    long sleep = Math.min(700L * (1L << (attempt - 1)), 5_000L);
                    refreshActiveEndpoint();
                    AppLog.info("[LT] preserveTags.batch " + batchNo +
                            " failed (" + e.getClass().getSimpleName() + "): " + e.getMessage() +
                            " -> retry in " + sleep + "ms");

                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return expandDeduped(uniqueOut, deduped, texts.size());
                    }

                    if (attempt == 3) throw last;
                }
            }
        }

        long tAll = System.currentTimeMillis() - tAll0;
        AppLog.info("[LT] preserveTags.BATCH done in " + tAll + "ms total");
        return expandDeduped(uniqueOut, deduped, texts.size());
    }
    public static List<String> translatePreservingTagsBatched(
            LibreTranslateApi api,
            List<String> texts,
            String source,
            String target,
            java.util.function.BooleanSupplier stop,
            ProgressListener progress
    ) throws IOException, InterruptedException {

        if (texts == null || texts.isEmpty()) return Collections.emptyList();

        DedupedTexts deduped = dedupeTexts(texts);
        List<String> uniqueTexts = deduped.uniqueTexts;

        long tAll0 = System.currentTimeMillis();
        AppLog.info("[LT] preserveTags.BATCH+PROGRESS start: items=" + texts.size() +
                " unique=" + uniqueTexts.size() +
                " chars=" + sumChars(uniqueTexts) + " " + source + "->" + target);

        int maxItems = effectiveBatchMaxItems();
        int maxChars = effectiveBatchMaxChars();
        List<List<String>> parts = chunksByChars(uniqueTexts, maxItems, maxChars);
        AppLog.info("[LT] preserveTags.batching: parts=" + parts.size()
                + " profile=" + (isGpuActive() ? "GPU" : "CPU")
                + " maxItems=" + maxItems + " maxChars=" + maxChars);

        int total = parts.size();
        if (progress != null) {
            progress.onProgress(0.0, "batch 0/" + total);
        }

        if (total <= 1) {
            // fallback to sequential for tiny jobs
            return translatePreservingTagsBatchedSequential(api, texts, source, target, stop, progress);
        }

        int concurrency = effectiveConcurrency();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        ExecutorCompletionService<PartResult> completion = new ExecutorCompletionService<>(executor);

        for (int i = 0; i < total; i++) {
            final int batchIndex = i;
            final List<String> part = parts.get(i);

            completion.submit(() -> {
                int attempt = 1;
                while (true) {
                    if (stop != null && stop.getAsBoolean()) {
                        throw new InterruptedException("cancelled");
                    }

                    try {
                        AppLog.info("[LT] preserveTags.batch " + (batchIndex + 1) + "/" + total +
                                " attempt " + attempt + "/3 items=" + part.size() +
                                " chars=" + sumChars(part));

                        List<String> translated = translatePreservingTags(api, part, source, target);
                        AppLog.info("[LT] preserveTags.batch " + (batchIndex + 1) + " OK");
                        return new PartResult(batchIndex, translated);
                    } catch (IOException e) {
                        if (attempt >= 3) {
                            throw e;
                        }
                        long sleep = Math.min(700L * (1L << (attempt - 1)), 5_000L);
                        refreshActiveEndpoint();
                        AppLog.info("[LT] preserveTags.batch " + (batchIndex + 1) +
                                " failed (" + e.getClass().getSimpleName() + "): " + e.getMessage() +
                                " -> retry in " + sleep + "ms");
                        Thread.sleep(sleep);
                        attempt++;
                    }
                }
            });
        }

        executor.shutdown();

        @SuppressWarnings("unchecked")
        List<String>[] results = new List[total];
        int finished = 0;
        while (finished < total) {
            Future<PartResult> future = completion.take();
            try {
                PartResult r = future.get();
                results[r.batchIndex] = r.translated;
                finished++;

                if (progress != null) {
                    progress.onProgress(finished / (double) total,
                            "batch " + finished + "/" + total + " (concurrency=" + concurrency + ")");
                }
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                } else if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                } else if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                } else {
                    throw new IOException(cause);
                }
            }
        }

        List<String> uniqueOut = new ArrayList<>(uniqueTexts.size());
        for (int i = 0; i < total; i++) {
            if (results[i] != null) uniqueOut.addAll(results[i]);
        }

        if (progress != null) {
            progress.onProgress(1.0, "done");
        }

        long tAll = System.currentTimeMillis() - tAll0;
        AppLog.info("[LT] preserveTags.BATCH+PROGRESS done in " + tAll + "ms total");
        return expandDeduped(uniqueOut, deduped, texts.size());
    }

    private static class PartResult {
        final int batchIndex;
        final List<String> translated;

        PartResult(int batchIndex, List<String> translated) {
            this.batchIndex = batchIndex;
            this.translated = translated;
        }
    }

    private static DedupedTexts dedupeTexts(List<String> texts) {
        Map<String, Integer> firstIndex = new LinkedHashMap<>();
        List<String> unique = new ArrayList<>();
        int[] mapToUnique = new int[texts.size()];

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            Integer idx = firstIndex.get(text);
            if (idx == null) {
                idx = unique.size();
                firstIndex.put(text, idx);
                unique.add(text);
            }
            mapToUnique[i] = idx;
        }
        return new DedupedTexts(unique, mapToUnique);
    }

    private static List<String> expandDeduped(List<String> uniqueOut, DedupedTexts deduped, int originalSize) {
        List<String> out = new ArrayList<>(originalSize);
        for (int i = 0; i < originalSize; i++) {
            int idx = deduped.mapToUnique[i];
            out.add(idx < uniqueOut.size() ? uniqueOut.get(idx) : null);
        }
        return out;
    }

    private static List<List<String>> chunksByChars(List<String> src, int maxItems, int maxChars) {
        List<List<String>> batches = new ArrayList<>();
        if (src == null || src.isEmpty()) return batches;

        List<String> cur = new ArrayList<>();
        int curChars = 0;

        for (String s : src) {
            int len = (s != null ? s.length() : 0);
            boolean overflowItems = cur.size() >= maxItems;
            boolean overflowChars = (curChars + len) > maxChars;

            if (!cur.isEmpty() && (overflowItems || overflowChars)) {
                batches.add(cur);
                cur = new ArrayList<>();
                curChars = 0;
            }
            cur.add(s);
            curChars += len;
        }
        if (!cur.isEmpty()) batches.add(cur);
        return batches;
    }

    // ===== startup and health check =====
    public static boolean isLtAlive() {
        return refreshActiveEndpoint();
    }

    public static void waitLtReady(int attempts, long sleepMs) throws InterruptedException {
        for (int i = 0; i < attempts; i++) {
            if (refreshActiveEndpoint()) return;
            Thread.sleep(sleepMs);
        }
        throw new RuntimeException("LibreTranslate did not become ready on " + endpointCandidates());
    }

    public static synchronized boolean ensureServerAvailable() {
        if (shutdownRequested || Thread.currentThread().isInterrupted()) {
            return false;
        }
        String gpuBase = normalizeBaseUrl(GPU_FALLBACK_BASE_URL);
        String cpuBase = normalizeBaseUrl(DEFAULT_BASE_URL);

        // Fast path: if endpoint is already alive on known ports, skip heavy capability checks/restarts.
        if (probeBaseUrl(gpuBase)) {
            activateEndpoint(gpuBase);
            return true;
        }
        if (probeBaseUrl(cpuBase)) {
            Process process = managedLtProcess;
            if (process != null && process.isAlive()) {
                activateEndpoint(cpuBase);
                return true;
            }
        }

        refreshCapabilityCache(false);
        boolean nvidiaPresent = hasNvidiaGpu();
        boolean localPythonCuda = nvidiaPresent && isLocalPythonCudaAvailable();
        boolean dockerUsable = isDockerUsable();
        AppLog.info("[LT] ensureServerAvailable: nvidia=" + nvidiaPresent
                + ", localPythonCuda=" + localPythonCuda
                + ", docker=" + dockerUsable
                + ", python=" + pythonExecutable());

        if (probeBaseUrl(cpuBase)) {
            Process process = managedLtProcess;
            boolean managedGpuReady = managedLtGpu && process != null && process.isAlive();

            // Reuse already running endpoint when:
            // - GPU is not available/preferred, or
            // - managed local GPU process is already alive on 5000.
            if (!nvidiaPresent || !localPythonCuda || managedGpuReady) {
                activateEndpoint(cpuBase);
                AppLog.info("[LT] server already running on " + cpuBase + ", reusing existing");
                return true;
            }
        }

        if (nvidiaPresent) {
            if (useGpuDocker && dockerUsable && !gpuDockerStartupFailed) {
                try {
                    AppLog.info("[LT] NVIDIA GPU detected, trying GPU LibreTranslate at " + gpuBase);
                    startGpuDockerProcess();
                    if (waitForSpecificEndpoint(gpuBase, 40, 1_500L)) {
                        return true;
                    }
                    // if server is not reachable after starting, mark failure
                    gpuDockerStartupFailed = true;
                } catch (IOException ex) {
                    gpuDockerStartupFailed = true;
                    AppLog.warn("[LT] GPU startup failed, will fall back if CPU is reachable: " + ex.getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } else if (useGpuDocker && dockerUsable) {
                AppLog.info("[LT] GPU Docker is known to be unavailable or previously failed; skipping GPU container startup.");
            } else if (!useGpuDocker) {
                AppLog.info("[LT] GPU Docker mode is disabled by settings; skipping container startup.");
            } else {
                AppLog.warn("[LT] NVIDIA GPU detected, but Docker is unavailable. GPU LibreTranslate is not started.");
            }

            if (localPythonCuda) {
                try {
                    AppLog.info("[LT] CUDA is available locally. Restarting LibreTranslate on " + cpuBase + " in GPU mode.");
                    restartLocalPythonGpuProcessOn5000();
                    if (waitForSpecificEndpoint(cpuBase, 40, 1_000L)) {
                        AppLog.info("[LT] local LibreTranslate is now running on " + cpuBase + " (GPU)");
                        return true;
                    }
                } catch (IOException ex) {
                    AppLog.warn("[LT] local GPU restart on " + cpuBase + " failed: " + ex.getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } else {
                AppLog.warn("[LT] NVIDIA GPU detected, but local Python LibreTranslate cannot use CUDA.");
            }
        }

        if (probeBaseUrl(cpuBase)) {
            activateEndpoint(cpuBase);
            if (nvidiaPresent) {
                AppLog.warn("[LT] GPU endpoint " + gpuBase + " is not reachable. Falling back to CPU on " + cpuBase);
            }
            return true;
        }

        try {
            startLtProcess();
            waitLtReady(80, 1_500L);
            return refreshActiveEndpoint();
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppLog.error("[LT] unable to start or reconnect LibreTranslate: " + ex.getMessage());
            return false;
        }
    }

    public static Process startLtProcess() throws IOException {
        // OPTIMIZED: Check if server is already running before killing competing processes
        if (probeBaseUrl("http://127.0.0.1:5000/") || probeBaseUrl("http://127.0.0.1:5001/")) {
            AppLog.info("[LT] LibreTranslate already running, skipping restart");
            return managedLtProcess;
        }
        
        stopCompetingLibreTranslateProcesses(Set.of(5000, 5001));

        if (hasNvidiaGpu() && useGpuDocker && isDockerUsable() && !gpuDockerStartupFailed) {
            try {
                AppLog.info("[LT] trying docker GPU container");
                return startGpuDockerProcess();
            } catch (IOException ex) {
                gpuDockerStartupFailed = true;
                AppLog.warn("[LT] docker GPU container start failed, trying CPU fallbacks: " + ex.getMessage());
            }
        } else if (hasNvidiaGpu() && useGpuDocker && isDockerUsable()) {
            AppLog.info("[LT] skipping docker GPU container due previous nonrecoverable failure.");
        } else if (!useGpuDocker) {
            AppLog.info("[LT] GPU Docker mode is disabled by settings; use CPU or local process.");
        }

        // 1) LibreTranslate CLI Å ĆøÅ Ā· PATH
        if (isOnPath("libretranslate")) {
            AppLog.info("[LT] trying libretranslate CLI");
            return new ProcessBuilder(
                    "libretranslate",
                    "--host", "127.0.0.1",
                    "--port", "5000"
            )
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }

        // 2) local exe Åā‚¬ÅĀøÅ Ā´Å Ā¾Å Ā¼ ÅĀ Å Ć¦Åā‚¬Å Ā¾Å Ā³Åā‚¬Å Ā°Å Ā¼Å Ā¼Å Ā¾Å Ā¹
        File localExe = new File("libretranslate.exe");
        if (localExe.exists()) {
            AppLog.info("[LT] trying local libretranslate.exe");
            return new ProcessBuilder(
                    localExe.getAbsolutePath(),
                    "--host", "127.0.0.1",
                    "--port", "5000"
            )
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }

        try {
            return startLocalPythonLibreTranslateProcess();
        } catch (IOException ex) {
            AppLog.warn("[LT] local python LibreTranslate start failed: " + ex.getMessage());
        }

        // 4) Docker Åā€Å Ā¾Å Ā»ÅĀÅ Å—Å Ā¾ Å Ā² ÅĀÅ Ā°Å Ā¼Å Ā¾Å Ā¼ Å Å—Å Ā¾Å Ā½Åā€ Å Āµ
        if (isDockerUsable()) {
            AppLog.info("[LT] trying docker CPU container");
            return new ProcessBuilder(
                    "docker", "run", "--rm",
                    "-p", "127.0.0.1:5000:5000",
                    "libretranslate/libretranslate"
            )
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }

        throw new IllegalStateException(
                "LibreTranslate launch failed: no working libretranslate/python/docker found"
        );
    }
    private static boolean isOnPath(String tool) {
        try {
            Process p = new ProcessBuilder("cmd", "/c", "where", tool).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
    private static boolean isDockerUsable() {
        refreshCapabilityCache(false);
        return cachedDockerUsable;
    }

    private static boolean isDockerUsableRaw() {
        try {
            Process p = new ProcessBuilder("docker", "version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Process startGpuDockerProcess() throws IOException {
        stopCompetingLibreTranslateProcesses(Set.of(5001));

        // OPTIMIZED: Docker GPU with maximum resource allocation and async processing
        Process process = new ProcessBuilder(
                "docker", "run", "--rm",
                "--gpus", "all",
                "--memory", "8g",              // OPTIMIZED: Allocate 8GB RAM
                "--cpus", "4.0",               // OPTIMIZED: 4 CPU cores
                "--shm-size", "2g",            // OPTIMIZED: Shared memory for GPU
                "-p", "127.0.0.1:5001:5000",
                "-e", "LIBRETRANSLATE_PORT=5000",
                "-e", "LIBRETRANSLATE_THREADS=8",
                "-e", "CUDA_DEVICE_ORDER=PCI_BUS_ID",
                "-e", "CUDA_LAUNCH_BLOCKING=0",
                "libretranslate/libretranslate:latest-cuda"
        )
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        managedLtProcess = process;
        managedLtGpu = true;
        tryRaiseProcessPriority(process, "High");
        return process;
    }

    private static Process startLocalPythonLibreTranslateProcess() throws IOException {
        File runtimeRoot = getPersistentArgosRuntimeRoot();
        File configDir = new File(runtimeRoot, "config");
        File dataDir = new File(runtimeRoot, "data");
        File cacheDir = new File(runtimeRoot, "cache");
        File packagesDir = new File(runtimeRoot, "packages");

        configDir.mkdirs();
        dataDir.mkdirs();
        cacheDir.mkdirs();
        packagesDir.mkdirs();

        boolean requestCuda = hasNvidiaGpu() && isLocalPythonCudaAvailable();
        if (hasNvidiaGpu() && !requestCuda) {
            AppLog.warn("[LT] NVIDIA detected, but local Python LibreTranslate has no CUDA support. Starting in CPU mode.");
        }

        AppLog.info("[LT] trying local python LibreTranslate on 127.0.0.1:5000"
                + " (" + (requestCuda ? "GPU" : "CPU") + ")");
        AppLog.info("[LT] Argos runtime: " + runtimeRoot.getAbsolutePath());

        stopCompetingLibreTranslateProcesses(Set.of(5000));

        ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable(),
                "-m", "libretranslate.main",
                "--host", "127.0.0.1",
                "--port", "5000",
                "--disable-web-ui"
        );

        Map<String, String> env = pb.environment();
        env.put("XDG_CONFIG_HOME", configDir.getAbsolutePath());
        env.put("XDG_DATA_HOME", dataDir.getAbsolutePath());
        env.put("XDG_CACHE_HOME", cacheDir.getAbsolutePath());
        env.put("ARGOS_PACKAGES_DIR", packagesDir.getAbsolutePath());
        env.put("ARGOS_DEVICE_TYPE", requestCuda ? "cuda" : "cpu");
        env.putIfAbsent("PYTHONUTF8", "1");
        env.putIfAbsent("PYTHONUNBUFFERED", "1");

        // OPTIMIZED: Use all CPU cores for LibreTranslate, more for GPU
        int threads = Runtime.getRuntime().availableProcessors();
        if (requestCuda) {
            threads = Math.max(threads * 2, 16);
        }
        env.putIfAbsent("OMP_NUM_THREADS", String.valueOf(threads));
        env.putIfAbsent("OPENBLAS_NUM_THREADS", String.valueOf(threads));
        env.putIfAbsent("MKL_NUM_THREADS", String.valueOf(threads));
        env.putIfAbsent("NUMEXPR_NUM_THREADS", String.valueOf(threads));
        
        // GPU optimization flags for NVIDIA CUDA
        if (requestCuda) {
            env.putIfAbsent("CUDA_DEVICE_ORDER", "PCI_BUS_ID");
            env.putIfAbsent("CUDA_LAUNCH_BLOCKING", "0");  // Async GPU execution
            env.putIfAbsent("TF_FORCE_GPU_ALLOW_GROWTH", "true");  // Avoid OOM
        }

        Process process = pb
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        managedLtProcess = process;
        managedLtGpu = requestCuda;
        tryRaiseProcessPriority(process, "High");
        return process;
    }

    public static void requestTranslationPerformanceMode() {
        if (PERFORMANCE_MODE_LEASES.incrementAndGet() == 1) {
            long currentPid = ProcessHandle.current().pid();
            // Maximize CPU for preparation/dedup/glossary + translation IPC,
            // even if the UI becomes less responsive while a translation is running.
            tryRaiseProcessPriorityByPid(currentPid, "High", "APP");
        }

        Process ltProcess = managedLtProcess;
        if (ltProcess != null && ltProcess.isAlive()) {
            tryRaiseProcessPriority(ltProcess, "High");
        }
    }

    public static void restoreTranslationPerformanceMode() {
        int leases = PERFORMANCE_MODE_LEASES.updateAndGet(current -> Math.max(0, current - 1));
        if (leases == 0) {
            long currentPid = ProcessHandle.current().pid();
            tryRaiseProcessPriorityByPid(currentPid, "High", "APP");
        }
    }

    public static void shutdown() {
        shutdownRequested = true;
        cancelInFlight();
        stopManagedLtProcess();
        savePersistentTranslationCache();
    }

    private static File getPersistentArgosRuntimeRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return new File(localAppData, "LocalizationEditorSC2KSP\\argos-runtime");
        }
        return new File(System.getProperty("user.home"), ".localization-editor-argos-runtime");
    }

    private static Path getPersistentTranslationCachePath() {
        File runtimeRoot = getPersistentArgosRuntimeRoot();
        runtimeRoot.mkdirs();
        return runtimeRoot.toPath().resolve("translation-cache.xml");
    }

    private static void restartLocalPythonGpuProcessOn5000() throws IOException {
        // OPTIMIZED: Reuse already running GPU process instead of restarting
        Process process = managedLtProcess;
        if (process != null && process.isAlive() && managedLtGpu) {
            AppLog.info("[LT] GPU process already running on 127.0.0.1:5000, reusing existing");
            return;
        }
        
        // Process is not running or is CPU mode - restart with GPU
        stopManagedLtProcess();
        stopCompetingLibreTranslateProcesses(Set.of(5000, 5001));
        startLocalPythonLibreTranslateProcess();
    }

    private static void stopManagedLtProcess() {
        Process process = managedLtProcess;
        managedLtProcess = null;
        managedLtGpu = false;
        if (process == null) {
            return;
        }
        destroyProcess(process.toHandle(), "[LT] stopped managed LibreTranslate process");
    }

    private static void stopCompetingLibreTranslateProcesses(Set<Integer> ports) {
        long currentPid = ProcessHandle.current().pid();
        ProcessHandle.allProcesses()
                .filter(handle -> handle.pid() != currentPid)
                .filter(handle -> handle.isAlive())
                .filter(handle -> looksLikeLibreTranslateProcess(handle, ports))
                .forEach(handle -> destroyProcess(handle,
                        "[LT] stopped stale LibreTranslate process (pid=" + handle.pid() + ")"));
    }

    private static boolean looksLikeLibreTranslateProcess(ProcessHandle handle, Set<Integer> ports) {
        ProcessHandle.Info info = handle.info();
        String command = info.command().orElse("").toLowerCase();
        String commandLine = info.commandLine().orElse("").toLowerCase();
        if (!command.contains("python") && !command.contains("libretranslate")) {
            return false;
        }
        Set<Integer> checkedPorts = (ports == null) ? Set.of() : ports;
        boolean portMatched = checkedPorts.isEmpty();
        if (!portMatched) {
            for (Integer port : checkedPorts) {
                if (port != null && commandLine.contains(String.valueOf(port))) {
                    portMatched = true;
                    break;
                }
            }
        }
        if (!portMatched) {
            return false;
        }
        return commandLine.contains("libretranslate.main")
                || commandLine.contains("libretranslate.exe")
                || commandLine.contains(" libretranslate ");
    }

    private static void destroyProcess(ProcessHandle handle, String successLog) {
        try {
            handle.destroy();
            try {
                handle.onExit().get(java.time.Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                if (handle.isAlive()) {
                    handle.destroyForcibly();
                }
            }
            AppLog.info(successLog);
        } catch (Exception ex) {
            AppLog.warn("[LT] failed to stop stale LibreTranslate process: " + ex.getMessage());
        }
    }

    private static String safeErrorBody(retrofit2.Response<?> resp) {
        if (resp == null) {
            return "null";
        }
        okhttp3.ResponseBody errorBody = resp.errorBody();
        if (errorBody == null) {
            return "null";
        }
        try {
            return errorBody.string();
        } catch (IOException e) {
            return "<failed to read error body: " + e.getMessage() + ">";
        }
    }

    private static boolean probeLocalPythonCudaSupport() {
        if (probeLocalPythonCuda(
                "import ctranslate2; print('1' if ctranslate2.get_cuda_device_count() > 0 else '0')")) {
            return true;
        }
        try {
            return probeLocalPythonCuda(
                    "import torch; print('1' if torch.cuda.is_available() else '0')");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isLocalPythonCudaAvailable() {
        refreshCapabilityCache(false);
        return cachedLocalPythonCuda;
    }

    private static boolean probeLocalPythonCuda(String command) {
        try {
            Process p = new ProcessBuilder(pythonExecutable(), "-c", command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            byte[] out = p.getInputStream().readAllBytes();
            if (p.waitFor() != 0) {
                return false;
            }
            return new String(out).trim().equals("1");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean probeNvidiaGpu() {
        if (probeNvidiaSmi("nvidia-smi")) {
            return true;
        }
        return probeNvidiaSmi("C:\\Windows\\System32\\nvidia-smi.exe");
    }

    private static boolean hasNvidiaGpu() {
        refreshCapabilityCache(false);
        return cachedNvidiaGpu;
    }

    private static boolean shouldRefreshCapabilityCache(long now, boolean force) {
        if (force) {
            return true;
        }
        return capabilityCacheTs <= 0 || (now - capabilityCacheTs) >= CAPABILITY_CACHE_TTL_MS;
    }

    private static void refreshCapabilityCache(boolean force) {
        long now = System.currentTimeMillis();
        synchronized (TranslationService.class) {
            if (!shouldRefreshCapabilityCache(now, force)) {
                return;
            }
        }

        // Heavy probes are executed outside synchronized block.
        boolean nvidia = probeNvidiaGpu();
        boolean docker = isDockerUsableRaw();
        boolean localCuda = nvidia && probeLocalPythonCudaSupport();

        synchronized (TranslationService.class) {
            long now2 = System.currentTimeMillis();
            if (!shouldRefreshCapabilityCache(now2, force)) {
                return;
            }
            cachedNvidiaGpu = nvidia;
            cachedDockerUsable = docker;
            cachedLocalPythonCuda = localCuda;
            capabilityCacheTs = now2;
        }
    }

    private static boolean probeNvidiaSmi(String executable) {
        try {
            Process p = new ProcessBuilder(executable, "-L")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static synchronized String pythonExecutable() {
        if (cachedPythonExecutable != null && !cachedPythonExecutable.isBlank()) {
            return cachedPythonExecutable;
        }

        String envPython = System.getenv("LT_PYTHON");
        if (envPython != null && !envPython.isBlank() && new File(envPython).exists()) {
            cachedPythonExecutable = envPython;
            return cachedPythonExecutable;
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            File pythonRoot = new File(localAppData, "Programs\\Python");
            File[] dirs = pythonRoot.listFiles(File::isDirectory);
            if (dirs != null && dirs.length > 0) {
                Arrays.sort(dirs, (a, b) -> b.getName().compareToIgnoreCase(a.getName()));
                for (File dir : dirs) {
                    File exe = new File(dir, "python.exe");
                    if (exe.exists()) {
                        cachedPythonExecutable = exe.getAbsolutePath();
                        AppLog.info("[LT] selected python executable: " + cachedPythonExecutable);
                        return cachedPythonExecutable;
                    }
                }
            }
        }

        cachedPythonExecutable = "python";
        return cachedPythonExecutable;
    }

    public static boolean isPersistentCacheEnabled() {
        return persistentCacheEnabled;
    }

    public static void setPersistentCacheEnabled(boolean enabled) {
        persistentCacheEnabled = enabled;
    }

    public static boolean isGpuDockerEnabled() {
        return useGpuDocker;
    }

    public static void setGpuDockerEnabled(boolean enabled) {
        useGpuDocker = enabled;
        if (!enabled) {
            gpuDockerStartupFailed = true;
        }
    }

    public static void clearTranslationCache() {
        synchronized (TRANSLATION_CACHE) {
            TRANSLATION_CACHE.clear();
        }
        Path path = getPersistentTranslationCachePath();
        try {
            Files.deleteIfExists(path);
            AppLog.info("[LT] translation cache cleared");
        } catch (Exception ex) {
            AppLog.warn("[LT] failed to delete translation cache file: " + ex.getMessage());
        }
        persistentCacheLoaded = false;
    }

    private static void ensurePersistentCacheLoaded() {
        if (!persistentCacheEnabled) {
            // Keep runtime cache only; avoid loading disk persistence.
            persistentCacheLoaded = true;
            return;
        }

        if (persistentCacheLoaded) {
            return;
        }
        synchronized (CACHE_IO_LOCK) {
            if (persistentCacheLoaded) {
                return;
            }

            Path path = getPersistentTranslationCachePath();
            if (Files.isRegularFile(path)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(path)) {
                    props.loadFromXML(in);
                    for (String key : props.stringPropertyNames()) {
                        TRANSLATION_CACHE.put(key, props.getProperty(key));
                    }
                    AppLog.info("[LT] persistent translation cache loaded: " + props.size() + " entries");
                } catch (Exception ex) {
                    AppLog.warn("[LT] failed to load persistent translation cache: " + ex.getMessage());
                }
            }
            persistentCacheLoaded = true;
        }
    }

    private static void installCacheShutdownHook() {
        if (shutdownHookInstalled) {
            return;
        }
        synchronized (CACHE_IO_LOCK) {
            if (shutdownHookInstalled) {
                return;
            }
            Runtime.getRuntime().addShutdownHook(new Thread(
                    TranslationService::savePersistentTranslationCache,
                    "lt-cache-shutdown-save"
            ));
            shutdownHookInstalled = true;
        }
    }

    private static void savePersistentTranslationCache() {
        if (!persistentCacheEnabled) {
            AppLog.info("[LT] persistent translation cache saving is disabled");
            return;
        }

        ensurePersistentCacheLoaded();
        synchronized (CACHE_IO_LOCK) {
            try {
                Path path = getPersistentTranslationCachePath();
                Properties props = new Properties();
                synchronized (TRANSLATION_CACHE) {
                    for (Map.Entry<String, String> entry : TRANSLATION_CACHE.entrySet()) {
                        props.setProperty(entry.getKey(), entry.getValue());
                    }
                }
                try (OutputStream out = Files.newOutputStream(path)) {
                    props.storeToXML(out, "Localization Editor translation cache", "UTF-8");
                }
                AppLog.info("[LT] persistent translation cache saved: " + props.size() + " entries");
            } catch (Exception ex) {
                AppLog.warn("[LT] failed to save persistent translation cache: " + ex.getMessage());
            }
        }
    }

    private static void tryRaiseProcessPriority(Process process, String priorityClass) {
        if (process == null) {
            return;
        }
        long pid = process.pid();
        if (pid <= 0) {
            return;
        }
        tryRaiseProcessPriorityByPid(pid, priorityClass, "LT");
    }

    private static void tryRaiseProcessPriorityByPid(long pid, String priorityClass, String label) {
        if (pid <= 0) {
            return;
        }
        String ps = "$p=Get-Process -Id " + pid
                + " -ErrorAction SilentlyContinue; "
                + "if($p){$p.PriorityClass='" + priorityClass + "'}";
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-Command", ps)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            AppLog.info("[LT] process priority requested (" + label + "): " + priorityClass + " (pid=" + pid + ")");
        } catch (Exception ex) {
            AppLog.warn("[LT] failed to raise process priority: " + ex.getMessage());
        }
    }
    static List<String> translateBatch(
            LibreTranslateApi api,
            List<String> texts,
            String source, String target
    ) throws IOException {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("q", texts);               //  <= ARRAY
        body.put("source", source);         // "auto" or ISO code
        body.put("target", target);         // ISO code
        body.put("format", "text");


        TranslateRequest translateRequest = new TranslateRequest(String.join(SEP, texts), source, target);
        Call<TranslateResponse> call = requireApi().translate(translateRequest);
        //   call.timeout().timeout(600, java.util.concurrent.TimeUnit.SECONDS);
        TranslateResponse resp = executeTracked(call);
        if (resp == null || resp.getTranslatedText() == null) {
            throw new IOException("Empty translation response");
        }
        AppLog.info(translateRequest + "->" + resp);


        List<String> translated = Arrays.asList(resp.getTranslatedText().split(SEP));


        if (translated.size() != texts.size()) {

            throw new IllegalStateException(
                    "sent: " + (String.join(SEP, texts)) +
                            "\n received: " + resp.getTranslatedText());
        }
//        AppLog.info(texts.size() + "
        return translated;
//        Response<List<String>> resp = api.translate(body).execute();
//        if (!resp.isSuccessful() || resp.body() == null) {
//            throw new IOException("LT error " + resp.code() + ": " + resp.errorBody());
//        }
//        return resp.body();                 // 
    }

    public static List<String> translateOnceEnRu(List<String> texts) throws IOException {
        long t0 = System.nanoTime();
        // single run, hardcoded EN -> RU
        List<String> out = translateBatch(TranslationService.api, texts, "en", "ru");
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        AppLog.info("EN->RU: batch=" + texts.size() + " took " + ms + " ms");
        return out;
    }

    static <T> List<List<T>> chunks(List<T> src, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < src.size(); i += size) {
            out.add(src.subList(i, Math.min(i + size, src.size())));
        }
        return out;
    }

    public static Map<String, List<String>> translateAll(
            List<String> texts,
            String sourceIso,
            List<String> targetsIso
    ) throws IOException {
        return translateAll(texts, sourceIso, targetsIso, null, null);
    }


    private static int sumChars(List<String> list) {
        int n = 0;
        if (list == null) return 0;
        for (String s : list) n += (s == null ? 0 : s.length());
        return n;
    }

    public static void saveTranslated(File originalFile, String targetLang, String translatedText) throws IOException {
        File out = FileUtil.resolveTargetFile(originalFile, targetLang);
        AppLog.info("[SAVE] from: " + originalFile.getAbsolutePath());
        AppLog.info("[SAVE] to  : " + out.getAbsolutePath());
        FileUtil.writeUtf8Atomic(out, translatedText);
        AppLog.info("[SAVE] done");
    }

}
